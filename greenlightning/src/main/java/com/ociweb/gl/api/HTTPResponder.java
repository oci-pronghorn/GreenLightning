package com.ociweb.gl.api;

import com.ociweb.pronghorn.network.config.HTTPContentType;
import com.ociweb.pronghorn.network.config.HTTPHeaderDefaults;
import com.ociweb.pronghorn.network.http.HeaderWritable;
import com.ociweb.pronghorn.network.http.HeaderWriter;
import com.ociweb.pronghorn.pipe.ChannelReader;
import com.ociweb.pronghorn.pipe.ChannelWriter;

public class HTTPResponder {

	private final HTTPResponseService responseService;
	
	private long connectionId;
	private long sequenceCode;
	
	private boolean hasContinuation;
	private int statusCode = 200;	
	private HTTPContentType contentType;
	private HeaderWritable headers;
	private Writable writable;
	private long lastCancelledConnectionId = -1;
	private boolean closedResponse;//can only set when we have no writable

	public HTTPResponder(MsgCommandChannel<?> commandChannel, int maximumPayloadSize) {
		clearAll();
	    
	    int maximumMessages = 4;
	    this.responseService = commandChannel.newHTTPResponseService(maximumMessages, maximumPayloadSize);
	    	    
	}

	private void clearAll() {
		this.connectionId = -1;
		this.sequenceCode = -1;	
		this.writable = null;
		this.headers = null;
		this.contentType = null;
		this.statusCode = 200;
		this.closedResponse = false;
	}

	/**
	 *
	 * @param reader ChannelReader arg used to set connectionId and sequenceCode
	 * @return <code>true</code> if Pipe.hasContentToRead(pipe) <p> <code>false</code> if connectionId >= 0 && sequenceCode >= 0
	 */
	public boolean readHandoffData(ChannelReader reader) {
		
	
		if (this.writable != null) {
		
			connectionId = reader.readPackedLong();
			sequenceCode = reader.readPackedLong();
			
			if (lastCancelledConnectionId == connectionId) {
				//already closed so clear, we can not send a response
				clearAll();
				return true;
			}
			
			if (responseService.publishHTTPResponse(connectionId, sequenceCode, 
			        								hasContinuation, headers, statusCode, contentType, writable)) {
				clearAll();				
				return true;
			} else {
				return false;
			}
			
		} else {
			
			//NB: if the connection was closed before we consumed the id values
			if (closedResponse) {
				
				if (publishCanceledResponse()) {
					clearAll();
					return true;
				} else {
					return false;
				}
			}
						
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
				
				if (lastCancelledConnectionId == connectionId) {
					connectionId = -1;//already closed so clear, we will not be getting a response.
					sequenceCode = -1;
				}
				
				
			}
	
			
			return true;
		}
		
	}

	public boolean respondWith(boolean hasContinuation, HeaderWritable headers, HTTPContentType contentType, Writable writable) {

   		if (null == this.writable & !closedResponse) {
   			if (connectionId>=0 && sequenceCode>=0) {
   				
   				if (responseService.publishHTTPResponse(connectionId, sequenceCode, 
   						hasContinuation, headers, statusCode, contentType, writable)) {
   					clearAll();
   					
   					return true;
   				} else {
   					return false;
   				}
   				
   			} else {
   				
   				if (this.writable != null) {
   					//can't store since we are waiting for the con and seq
   					return false;
   				} else {
   					
   					//store data to write later.
   					this.hasContinuation = hasContinuation;
   					this.headers = headers;
   					this.writable = writable;
   					this.contentType = contentType;
   					
   					return true;
   					
   				}
   			}		
   		} else {   	
   			return false;
   		}
		
	}
	
	private final int cancelStatusCode = 504;// Gateway Timeout
	private HTTPContentType cancelType = null;
	private HeaderWritable cancelHeaderWritable = new HeaderWritable() {
		@Override
		public void write(HeaderWriter writer) {
			writer.write(HTTPHeaderDefaults.CONNECTION, "closed");
		}
	};
	private Writable cancelPayload = new Writable() {
		@Override
		public void write(ChannelWriter writer) {
		}
	};
	
	
	public boolean closed() {		
		if (writable==null) {
			closedResponse = true;
			return true;			
		} else {
			return false;
		}
		
	}

	private boolean publishCanceledResponse() {
		//send the cancel response....
		if (responseService.publishHTTPResponse(
		        connectionId, sequenceCode, 
		        false, cancelHeaderWritable, cancelStatusCode,                      
		        cancelType, cancelPayload)) {
			
			//keep in case we see it again.
			lastCancelledConnectionId = connectionId;
			
			clearAll();
			return true;
		} else {
			return false;
		}
	}

	

	/**
	 *
	 * @param statusCode int arg used in commandChannel.publishHTTPResponse
	 * @param hasContinuation boolean arg
	 * @Param headers HeaderWritable
	 * @param contentType HTTPContentType arg used in commandChannel.publishHTTPResponse
	 * @param writable Writable arg used in commandChannel.publishHTTPResponse
	 * @return publishResult if connectionId >= 0 && sequenceCode >= 0 <p> <code>false</code> if Pipe.hasContentToRead(pipe) else <code>true</code>
	 */
    public boolean respondWith(int statusCode, boolean hasContinuation, 
    		                   HeaderWritable headers, HTTPContentType contentType, Writable writable) {
		
   		if (null == this.writable & !closedResponse) {    	
	    	if (connectionId>=0 && sequenceCode>=0) {
	    		if (responseService.publishHTTPResponse(
	    				                       connectionId, sequenceCode,
	    				                       hasContinuation, headers, statusCode,                      
	    				                       contentType, writable)) {
	    			clearAll();
	    			return true;
	    		} else {
	    			return false;
	    		}
	    	} else {
	    		
	    		if (this.writable != null) {
					return false;
					
				} else {
	    		
		    		this.hasContinuation = hasContinuation;
		    		this.contentType = contentType;
		    		this.statusCode = statusCode;
		    		this.headers = headers;
		    		this.writable = writable;
			
					return true;
					
				}
	    	}
   		} else {
   			return false;
   		}
	}
    
	/**
	 *
	 * @param statusCode int arg used in commandChannel.publishHTTPResponse
	 * @param hasContinuation boolean arg
	 * @param contentType HTTPContentType arg used in commandChannel.publishHTTPResponse
	 * @param writable Writable arg used in commandChannel.publishHTTPResponse
	 * @return publishResult if connectionId >= 0 && sequenceCode >= 0 <p> <code>false</code> if Pipe.hasContentToRead(pipe) else <code>true</code>
	 */
   public boolean respondWith(int statusCode, boolean hasContinuation, 
		                      HTTPContentType contentType, Writable writable) {
		
  		if (null == this.writable & !closedResponse) { 	
   	
		   	if (connectionId>=0 && sequenceCode>=0) {
		   		if (responseService.publishHTTPResponse(connectionId, sequenceCode,
						                           statusCode, hasContinuation, contentType, writable)) {
		   		
		   			clearAll();
		   			return true;
		   		} else {
		   			return false;
		   		}
		   	} else {
		   		
		   		if (this.writable != null) {
						return false;				
					} else {
			    		this.hasContinuation = hasContinuation;
			    		this.contentType = contentType;
			    		this.statusCode = statusCode;
			    		this.headers = null;
			    		this.writable = writable;
									
						return true;				
					}
		   	}
  		} else {
  			return false;
  		}
	}

}
