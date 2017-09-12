package com.ociweb.gl.api;

import com.ociweb.pronghorn.network.config.HTTPContentType;
import com.ociweb.pronghorn.pipe.BlobReader;
import com.ociweb.pronghorn.pipe.BlobWriter;
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
			public void write(BlobWriter writer) {
				int size = Pipe.takeMsgIdx(pipe);												
				DataInputBlobReader<RawDataSchema> dataStream = Pipe.inputStream(pipe);
				dataStream.openLowLevelAPIField();
				dataStream.readInto(writer,dataStream.available());
				Pipe.confirmLowLevelRead(pipe, size);
				Pipe.releaseReadLock(pipe);
			}
		};
	}
		
	public void readReqesterData(BlobReader reader) {
		
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
		} else {
			//wait for second call
			
			////example of what the writer does
			//writer.writePackedLong(connectionId);
			//writer.writePackedLong(sequenceCode);
			
			connectionId = reader.readPackedLong();
			sequenceCode = reader.readPackedLong();
		}
		
	}
	
	public void respondWith(boolean hasContinuation, String headers, Writable writable) {
		
		if (connectionId>=0 && sequenceCode>=0) {
			commandChannel.publishHTTPResponse(connectionId, sequenceCode, 
				                           hasContinuation, headers, writable);
		    connectionId = -1;
		    sequenceCode = -1;
		} else {
			//store data to write later.
			this.hasContinuation = hasContinuation;
			this.headers = headers;
			
			storeData(writable);
				
		}
		
	}

	private void storeData(Writable writable) {
		Pipe.addMsgIdx(pipe, RawDataSchema.MSG_CHUNKEDSTREAM_1);
		DataOutputBlobWriter<RawDataSchema> outputStream = Pipe.outputStream(pipe);
		DataOutputBlobWriter.openField(outputStream);				
		writable.write(outputStream);
		DataOutputBlobWriter.closeLowLevelField(outputStream);
		Pipe.confirmLowLevelWrite(pipe,Pipe.sizeOf(RawDataSchema.instance, RawDataSchema.MSG_CHUNKEDSTREAM_1));
		Pipe.publishWrites(pipe);
	}
	
    public void respondWith(int statusCode, boolean hasContinuation, HTTPContentType contentType, Writable writable) {
		
    	if (connectionId>=0 && sequenceCode>=0) {
    		commandChannel.publishHTTPResponse(connectionId, sequenceCode, 
				                           statusCode, hasContinuation, contentType, writable);
		    connectionId = -1;
		    sequenceCode = -1;    		
    	} else {
    		
    		this.hasContinuation = hasContinuation;
    		this.contentType = contentType;
    		this.statusCode = statusCode;

			storeData(writable);
				
    	}
	}
	
	
}
