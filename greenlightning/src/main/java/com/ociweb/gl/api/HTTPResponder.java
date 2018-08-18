package com.ociweb.gl.api;

import com.ociweb.pronghorn.network.ClientConnection;
import com.ociweb.pronghorn.network.ClientCoordinator;
import com.ociweb.pronghorn.network.config.HTTPContentType;
import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;
import com.ociweb.pronghorn.network.http.HeaderWritable;
import com.ociweb.pronghorn.network.http.HeaderWriter;
import com.ociweb.pronghorn.pipe.ChannelReader;
import com.ociweb.pronghorn.pipe.ChannelWriter;
import com.ociweb.pronghorn.pipe.DataInputBlobReader;
import com.ociweb.pronghorn.pipe.DataOutputBlobWriter;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.RawDataSchema;

public class HTTPResponder {

	private final HTTPResponseService responseService;
	
	private long connectionId;
	private long sequenceCode;
	
	private boolean hasContinuation;
	private int statusCode;
	private HTTPContentType contentType;
	private HeaderWritable headers;
	
	private final Pipe<RawDataSchema> pipe;
	private final Writable writable;

	private final ClientCoordinator clientCoordinator;
	
	public HTTPResponder(MsgCommandChannel commandChannel,
            int maximumPayloadSize,
            MsgRuntime runtime) {
		this(commandChannel, maximumPayloadSize, runtime.builder.getClientCoordinator());
	}
	
	public HTTPResponder(MsgCommandChannel commandChannel,
            int maximumPayloadSize) {
		this(commandChannel, maximumPayloadSize, (ClientCoordinator)null);
	}
	
	/**
	 *
	 * @param commandChannel MsgCommandChannel arg used to make newHTTPResponseService
	 * @param maximumPayloadSize int arg used in pipe and commandChannel to set max payload
	 */
	public HTTPResponder(MsgCommandChannel commandChannel,
			             int maximumPayloadSize,
			             ClientCoordinator clientCoordinator) {
	    this.connectionId = -1;
	    this.sequenceCode = -1;
	    
	    int maximumMessages = 4;
	    
       // responseRelayChannel.ensureHTTPServerResponse(500, 1024);
	    this.responseService = commandChannel.newHTTPResponseService(maximumMessages, maximumPayloadSize);
	    	    
	    //temp space for only if they appear out of order.
		this.pipe = RawDataSchema.instance.newPipe(maximumMessages, maximumPayloadSize);
		this.pipe.initBuffers();
		this.clientCoordinator = clientCoordinator;
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

	/**
	 *
	 * @param reader ChannelReader arg used to set connectionId and sequenceCode
	 * @return <code>true</code> if Pipe.hasContentToRead(pipe) <p> <code>false</code> if connectionId >= 0 && sequenceCode >= 0
	 */
	public boolean readReqesterData(ChannelReader reader) {
		
		if (Pipe.hasContentToRead(pipe)) {
			
			if (null==headers) {
				//custom call
				responseService.publishHTTPResponse(connectionId, sequenceCode, 
                                                   statusCode, hasContinuation, 
                                                   contentType, writable);
			} else {
				//full headers call
				responseService.publishHTTPResponse(connectionId, sequenceCode, 
                        						   hasContinuation, headers, 200, writable);
			}
			connectionId = -1;
			sequenceCode = -1;	
			return true;
		} else {
			if (connectionId>=0 && sequenceCode>=0) {
			    //will not pick up new data, waiting for these to be consumed.
				if (connectionId != reader.readPackedLong()) {
					return false;
				}
				if (sequenceCode != reader.readPackedLong()) {
					return false;
				}
			} else {
				//wait for a following call
				
				////example of what the writer does
				//writer.writePackedLong(connectionId);
				//writer.writePackedLong(sequenceCode);
				
				connectionId = reader.readPackedLong();
				sequenceCode = reader.readPackedLong();
			}
			
			//TODO:if connection is cancelled return cancelled value and clear this.....
			
			
			return true;
		}
		
	}
	/**
	 *
	 * @param hasContinuation boolean arg determining if continuation exists
	 * @param headers HeaderWritable arg used in commandChannel.publishHTTPResponse
	 * @param writable Writable arg used in commandChannel.publishHTTPResponse
	 * @return <code>true</code> if connectionId >= 0 && sequenceCode >= 0 else <code>false</code> <p> <code>false</code> if Pipe.contentRemaning(pipe) != 0 else <code>true</code>
	 */
	public boolean respondWith(boolean hasContinuation, HeaderWritable headers, Writable writable) {

		if (connectionId>=0 && sequenceCode>=0) {
			
			if (responseService.publishHTTPResponse(connectionId, sequenceCode, 
				                           hasContinuation, headers, 200, writable)) {
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
	
	
	private HTTPContentType cancelType = HTTPContentTypeDefaults.PLAIN;
	private HeaderWritable cancelHeaderWritable = new HeaderWritable() {
		@Override
		public void write(HeaderWriter writer) {
		}
	};
	private Writable cancelPayload = new Writable() {
		@Override
		public void write(ChannelWriter writer) {
		}
	};
	
	/**
	 * set the response for all cancelled requests
	 * @param type HTTPContentType
	 * @param header HeaderWritable
	 * @param writable Writable
	 */
	public void setCancelResponse(HTTPContentType type, 
			                      HeaderWritable header, 
			                      Writable writable) {
		cancelType = type;
		cancelHeaderWritable = header;
		cancelPayload = writable;
	}

	//call this to see if connection is disconecting or invalid just before sending response.
	//clientCoordinator.connectionForSessionId(id)
	
	
	public void scanForCancelled() {
		
		if (connectionId!=-1) {
			//is the current waiting connection is cancelled
			//this is triggered by? what
		
			//TODO: can this be non HTTP ?
			
			final boolean alsoReturnDisconnected = true;
			ClientConnection con = (ClientConnection)clientCoordinator.connectionForSessionId(connectionId, alsoReturnDisconnected);
			
			
			
			
			
		
		}
				
	}
	
	public void cancelRequest(long connectionId) {
		//cancel those waiting if connetionId matches
		
		
	}
	

	/**
	 *
	 * @param statusCode int arg used in commandChannel.publishHTTPResponse
	 * @param hasContinuation boolean arg
	 * @param contentType HTTPContentType arg used in commandChannel.publishHTTPResponse
	 * @param writable Writable arg used in commandChannel.publishHTTPResponse
	 * @return publishResult if connectionId >= 0 && sequenceCode >= 0 <p> <code>false</code> if Pipe.hasContentToRead(pipe) else <code>true</code>
	 */
    public boolean respondWith(int statusCode, boolean hasContinuation, HTTPContentType contentType, Writable writable) {
		
    	if (connectionId>=0 && sequenceCode>=0) {
    		boolean publishResult = responseService.publishHTTPResponse(connectionId, sequenceCode,
				                           statusCode, hasContinuation, contentType, writable);
    		if (publishResult) {
    			connectionId = -1;
    			sequenceCode = -1; 
    		}
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

	private void storeData(Writable writable) {
		Pipe.addMsgIdx(pipe, RawDataSchema.MSG_CHUNKEDSTREAM_1);
		DataOutputBlobWriter<RawDataSchema> outputStream = Pipe.openOutputStream(pipe);				
		writable.write(outputStream);
		DataOutputBlobWriter.closeLowLevelField(outputStream);
		Pipe.confirmLowLevelWrite(pipe,Pipe.sizeOf(RawDataSchema.instance, RawDataSchema.MSG_CHUNKEDSTREAM_1));
		Pipe.publishWrites(pipe);
	}
	
}
