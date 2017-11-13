package com.ociweb.gl.api;

import com.ociweb.pronghorn.network.config.HTTPContentType;
import com.ociweb.pronghorn.pipe.ChannelReader;
import com.ociweb.pronghorn.pipe.ChannelWriter;
import com.ociweb.pronghorn.pipe.DataInputBlobReader;
import com.ociweb.pronghorn.pipe.DataOutputBlobWriter;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.RawDataSchema;

public class HTTPResponder {

	private final MsgCommandChannel commandChannel;
	
	private long connectionId;
	private long sequenceCode;
	
	private boolean hasContinuation;
	private int statusCode;
	private HTTPContentType contentType;
	private CharSequence headers;
	
	private final Pipe<RawDataSchema> pipe;
	private final Writable writable;	
	
	public HTTPResponder(MsgCommandChannel commandChannel, int maximumPayloadSize) {
		this.commandChannel = commandChannel;
	    this.connectionId = -1;
	    this.sequenceCode = -1;
	    
	    int maximumMessages = 4;
	    
	    commandChannel.ensureHTTPServerResponse(maximumMessages, maximumPayloadSize);
	    
	    //temp space for only if they appear out of order.
		this.pipe = RawDataSchema.instance.newPipe(maximumMessages, maximumPayloadSize);
		this.pipe.initBuffers();
		
	    this.writable = new Writable() {
			@Override
			public void write(ChannelWriter writer) {
				int msg = Pipe.takeMsgIdx(pipe);												
				DataInputBlobReader<RawDataSchema> dataStream = Pipe.inputStream(pipe);
				dataStream.openLowLevelAPIField();
				dataStream.readInto(writer,dataStream.available());
				Pipe.confirmLowLevelRead(pipe, Pipe.sizeOf(RawDataSchema.instance, msg));
				Pipe.releaseReadLock(pipe);
			}
		};
	}
		
	public boolean readReqesterData(ChannelReader reader) {
		
		if (Pipe.hasContentToRead(pipe)) {
			
			if (null==headers) {
				//custom call
				commandChannel.publishHTTPResponse(connectionId, sequenceCode, 
                                                   statusCode, hasContinuation, 
                                                   contentType, writable);
			} else {
				//full headers call
				commandChannel.publishHTTPResponse(connectionId, sequenceCode, 
                        						   hasContinuation, headers, writable);
			}
			connectionId = -1;
			sequenceCode = -1;	
			return true;
		} else {
			if (connectionId>=0 && sequenceCode>=0) {
			    //will not pick up new data, waiting for these to be consumed.
				return false;
			} else {
				//wait for a following call
				
				////example of what the writer does
				//writer.writePackedLong(connectionId);
				//writer.writePackedLong(sequenceCode);
				
				connectionId = reader.readPackedLong();
				sequenceCode = reader.readPackedLong();
				return true;
			}
		}
		
	}
	
	public boolean respondWith(boolean hasContinuation, String headers, Writable writable) {
		
		if (connectionId>=0 && sequenceCode>=0) {
			
			if (commandChannel.publishHTTPResponse(connectionId, sequenceCode, 
				                           hasContinuation, headers, writable)) {
			    connectionId = -1;
			    sequenceCode = -1;
			    return true;
			} else {
				return false;
			}
		    
		} else {
		 
			if (Pipe.contentRemaining(pipe)!=0) {
				//can't store since we are waiting for the con and seq
				return false;
			} else {
			
				//store data to write later.
				this.hasContinuation = hasContinuation;
				this.headers = headers;
				
				storeData(writable);
			
				return true;
				
			}
		}
		
	}

	private void storeData(Writable writable) {
		Pipe.addMsgIdx(pipe, RawDataSchema.MSG_CHUNKEDSTREAM_1);
		DataOutputBlobWriter<RawDataSchema> outputStream = Pipe.openOutputStream(pipe);				
		writable.write(outputStream);
		DataOutputBlobWriter.closeLowLevelField(outputStream);
		Pipe.confirmLowLevelWrite(pipe,Pipe.sizeOf(RawDataSchema.instance, RawDataSchema.MSG_CHUNKEDSTREAM_1));
		Pipe.publishWrites(pipe);
	}
	
    public boolean respondWith(int statusCode, boolean hasContinuation, HTTPContentType contentType, Writable writable) {
		
    	if (connectionId>=0 && sequenceCode>=0) {
    		boolean publishResult = commandChannel.publishHTTPResponse(connectionId, sequenceCode,
				                           statusCode, hasContinuation, contentType, writable);
		    connectionId = -1;
		    sequenceCode = -1; 
		    return publishResult;
    	} else {
    		
    		if (Pipe.hasContentToRead(pipe)) {
				return false;
				
			} else {
    		
	    		this.hasContinuation = hasContinuation;
	    		this.contentType = contentType;
	    		this.statusCode = statusCode;
	
				storeData(writable);
				
				return true;
				
			}
    	}
	}
	
	
}
