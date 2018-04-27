package com.ociweb.gl.api;

import com.ociweb.pronghorn.network.ClientCoordinator;
import com.ociweb.pronghorn.network.schema.ClientHTTPRequestSchema;
import com.ociweb.pronghorn.pipe.DataOutputBlobWriter;
import com.ociweb.pronghorn.pipe.FieldReferenceOffsetManager;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeWriter;

public class HTTPRequestService {

	private final MsgCommandChannel<?> msgCommandChannel;

	public HTTPRequestService(MsgCommandChannel<?> msgCommandChannel) {
		this.msgCommandChannel = msgCommandChannel;		
		msgCommandChannel.initFeatures |= MsgCommandChannel.NET_REQUESTER;
	}
	
	public HTTPRequestService(MsgCommandChannel<?> msgCommandChannel, int queueLength, int maxMessageSize) {
		this.msgCommandChannel = msgCommandChannel;
		MsgCommandChannel.growCommandCountRoom(msgCommandChannel, queueLength);
		msgCommandChannel.initFeatures |= MsgCommandChannel.NET_REQUESTER;
		
		msgCommandChannel.pcm.ensureSize(ClientHTTPRequestSchema.class, queueLength, maxMessageSize);
	}

	/**
	 *
	 * @param messageCount number to be multiplied by msgCommandChannel.httpRequest
	 * @return
	 */
	public boolean hasRoomFor(int messageCount) {		
		assert(msgCommandChannel.httpRequest!=null) : "Client side HTTP Request must be enabled";    	
		return Pipe.hasRoomForWrite(msgCommandChannel.httpRequest, 
				FieldReferenceOffsetManager.maxFragmentSize(
						Pipe.from(msgCommandChannel.httpRequest))*messageCount);
	}


	/**
	 *
	 * @param session ClientHostPortInstance used as an arg for PipeWriter
	 * @return true or false
	 */
	public boolean httpClose(ClientHostPortInstance session) {
		assert(msgCommandChannel.builder.getHTTPClientConfig() != null);
		assert((msgCommandChannel.initFeatures & MsgCommandChannel.NET_REQUESTER)!=0) : "must turn on NET_REQUESTER to use this method";
		
		if ((null==msgCommandChannel.goPipe || PipeWriter.hasRoomForWrite(msgCommandChannel.goPipe)) 
			&& PipeWriter.tryWriteFragment(msgCommandChannel.httpRequest, ClientHTTPRequestSchema.MSG_CLOSE_104)) {
								        	    
			PipeWriter.writeInt(msgCommandChannel.httpRequest, ClientHTTPRequestSchema.MSG_CLOSE_104_FIELD_SESSION_10, session.sessionId);
			PipeWriter.writeInt(msgCommandChannel.httpRequest, ClientHTTPRequestSchema.MSG_CLOSE_104_FIELD_PORT_1, session.port);
			PipeWriter.writeBytes(msgCommandChannel.httpRequest, ClientHTTPRequestSchema.MSG_CLOSE_104_FIELD_HOST_2, session.hostBytes);
		
			PipeWriter.publishWrites(msgCommandChannel.httpRequest);
		        		
			MsgCommandChannel.publishGo(1, msgCommandChannel.builder.netIndex(), msgCommandChannel);
			    	            
		    return true;
		}        
		return false;
	}

	public boolean httpGet(ClientHostPortInstance session, CharSequence route) {
		return httpGet(session,route,null);
	}

	/**
	 *
	 * @param session ClientHostPortInstance arg used in PipeWriter
	 * @param route CharSequence arg used in PipeWriter
	 * @param headers HeaderWritable arg used in PipeWriter
	 * @return true or false
	 */
	public boolean httpGet(ClientHostPortInstance session, CharSequence route, HeaderWritable headers) {
		assert(msgCommandChannel.builder.getHTTPClientConfig() != null);
		assert((msgCommandChannel.initFeatures & MsgCommandChannel.NET_REQUESTER)!=0) : "must turn on NET_REQUESTER to use this method";
		
		//////////////////////
		//get the cached connection ID so we need not deal with the host again
		/////////////////////
		if (session.getConnectionId()<0) {
			
			final long id = ClientCoordinator.lookup(
					                   ClientCoordinator.lookupHostId((CharSequence) session.host, msgCommandChannel.READER), 
					                   session.port, 
					                   session.sessionId);
		    if (id>=0) {
		    	session.setConnectionId(id);
		    }
			
		} 
		
		if ((null==msgCommandChannel.goPipe || PipeWriter.hasRoomForWrite(msgCommandChannel.goPipe))) {
		
			if (session.getConnectionId()<0) {
		
				if (PipeWriter.tryWriteFragment(msgCommandChannel.httpRequest, ClientHTTPRequestSchema.MSG_HTTPGET_100)) {
					PipeWriter.writeInt(msgCommandChannel.httpRequest, ClientHTTPRequestSchema.MSG_HTTPGET_100_FIELD_DESTINATION_11, msgCommandChannel.builder.lookupHTTPClientPipe(session.sessionId));
					PipeWriter.writeInt(msgCommandChannel.httpRequest, ClientHTTPRequestSchema.MSG_HTTPGET_100_FIELD_SESSION_10, session.sessionId);
					
		    		PipeWriter.writeInt(msgCommandChannel.httpRequest, ClientHTTPRequestSchema.MSG_HTTPGET_100_FIELD_PORT_1, session.port);
		    		PipeWriter.writeBytes(msgCommandChannel.httpRequest, ClientHTTPRequestSchema.MSG_HTTPGET_100_FIELD_HOST_2, session.hostBytes);
		    		
		    		PipeWriter.writeUTF8(msgCommandChannel.httpRequest, ClientHTTPRequestSchema.MSG_HTTPGET_100_FIELD_PATH_3, route);
					
					DataOutputBlobWriter<ClientHTTPRequestSchema> hw = Pipe.outputStream(msgCommandChannel.httpRequest);
				    DataOutputBlobWriter.openField(hw);
				    if (null!=headers) {
				    	headers.write(msgCommandChannel.headerWriter.target(hw));
				    }
				    hw.closeHighLevelField(ClientHTTPRequestSchema.MSG_HTTPGET_100_FIELD_HEADERS_7);
					
					PipeWriter.publishWrites(msgCommandChannel.httpRequest);
					
					MsgCommandChannel.publishGo(1, msgCommandChannel.builder.netIndex(), msgCommandChannel);
					
					return true;
				}
			} else {
				if (PipeWriter.tryWriteFragment(msgCommandChannel.httpRequest, ClientHTTPRequestSchema.MSG_FASTHTTPGET_200)) {
					PipeWriter.writeInt(msgCommandChannel.httpRequest, ClientHTTPRequestSchema.MSG_FASTHTTPGET_200_FIELD_DESTINATION_11, msgCommandChannel.builder.lookupHTTPClientPipe(session.sessionId));
					PipeWriter.writeInt(msgCommandChannel.httpRequest, ClientHTTPRequestSchema.MSG_FASTHTTPGET_200_FIELD_SESSION_10, session.sessionId);
					
		    		PipeWriter.writeInt(msgCommandChannel.httpRequest, ClientHTTPRequestSchema.MSG_FASTHTTPGET_200_FIELD_PORT_1, session.port);
		    		PipeWriter.writeBytes(msgCommandChannel.httpRequest, ClientHTTPRequestSchema.MSG_FASTHTTPGET_200_FIELD_HOST_2, session.hostBytes);
		    		PipeWriter.writeLong(msgCommandChannel.httpRequest, ClientHTTPRequestSchema.MSG_FASTHTTPGET_200_FIELD_CONNECTIONID_20, session.getConnectionId());
		    		PipeWriter.writeUTF8(msgCommandChannel.httpRequest, ClientHTTPRequestSchema.MSG_FASTHTTPGET_200_FIELD_PATH_3, route);
		
					DataOutputBlobWriter<ClientHTTPRequestSchema> hw = Pipe.outputStream(msgCommandChannel.httpRequest);
				    DataOutputBlobWriter.openField(hw);
				    if (null!=headers) {
				    	headers.write(msgCommandChannel.headerWriter.target(hw));
				    }
				    hw.closeHighLevelField(ClientHTTPRequestSchema.MSG_FASTHTTPGET_200_FIELD_HEADERS_7);
										
					PipeWriter.publishWrites(msgCommandChannel.httpRequest);
					
					MsgCommandChannel.publishGo(1, msgCommandChannel.builder.netIndex(), msgCommandChannel);
					
					return true;
				}
				
			}
			
		}
		return false;
	}

	public boolean httpPost(ClientHostPortInstance session, CharSequence route, Writable payload) {
		return httpPost(session, route, null, payload);
	}

	/**
	 *
	 * @param session ClientHostPortInstance arg used in PipeWriter
	 * @param route CharSequence arg used in PipeWriter
	 * @param headers HeaderWritable arg used in PipeWriter
	 * @param payload
	 * @return true if session.getConnnectionId() < 0 <p> false otherwise
	 */
	public boolean httpPost(ClientHostPortInstance session, CharSequence route, HeaderWritable headers, Writable payload) {
		assert((msgCommandChannel.initFeatures & MsgCommandChannel.NET_REQUESTER)!=0) : "must turn on NET_REQUESTER to use this method";
		
		if (null==msgCommandChannel.goPipe || PipeWriter.hasRoomForWrite(msgCommandChannel.goPipe)) { 
						
			if (session.getConnectionId()<0) {
				if (PipeWriter.tryWriteFragment(msgCommandChannel.httpRequest, ClientHTTPRequestSchema.MSG_HTTPPOST_101)) {
					
					PipeWriter.writeInt(msgCommandChannel.httpRequest, ClientHTTPRequestSchema.MSG_HTTPPOST_101_FIELD_DESTINATION_11, msgCommandChannel.builder.lookupHTTPClientPipe(session.sessionId));
					PipeWriter.writeInt(msgCommandChannel.httpRequest, ClientHTTPRequestSchema.MSG_HTTPPOST_101_FIELD_SESSION_10, session.sessionId);
					PipeWriter.writeInt(msgCommandChannel.httpRequest, ClientHTTPRequestSchema.MSG_HTTPPOST_101_FIELD_PORT_1, session.port);
					PipeWriter.writeBytes(msgCommandChannel.httpRequest, ClientHTTPRequestSchema.MSG_HTTPPOST_101_FIELD_HOST_2, session.hostBytes);
					PipeWriter.writeUTF8(msgCommandChannel.httpRequest, ClientHTTPRequestSchema.MSG_HTTPPOST_101_FIELD_PATH_3, route);
					
					DataOutputBlobWriter<ClientHTTPRequestSchema> hw = Pipe.outputStream(msgCommandChannel.httpRequest);
					DataOutputBlobWriter.openField(hw);
					if (null!=headers) {
						headers.write(msgCommandChannel.headerWriter.target(hw));
					}
					hw.closeHighLevelField(ClientHTTPRequestSchema.MSG_HTTPPOST_101_FIELD_HEADERS_7);
					
					DataOutputBlobWriter<ClientHTTPRequestSchema> pw = Pipe.outputStream(msgCommandChannel.httpRequest);
					DataOutputBlobWriter.openField(pw);
					payload.write(pw);
					pw.closeHighLevelField(ClientHTTPRequestSchema.MSG_HTTPPOST_101_FIELD_PAYLOAD_5);
					
					PipeWriter.publishWrites(msgCommandChannel.httpRequest);
					
					MsgCommandChannel.publishGo(1, msgCommandChannel.builder.netIndex(), msgCommandChannel);
					
					return true;
				}
				
			} else {
				
				if (PipeWriter.tryWriteFragment(msgCommandChannel.httpRequest, ClientHTTPRequestSchema.MSG_FASTHTTPPOST_201)) {
					
					PipeWriter.writeInt(msgCommandChannel.httpRequest, ClientHTTPRequestSchema.MSG_FASTHTTPPOST_201_FIELD_DESTINATION_11, msgCommandChannel.builder.lookupHTTPClientPipe(session.sessionId));
					PipeWriter.writeInt(msgCommandChannel.httpRequest, ClientHTTPRequestSchema.MSG_FASTHTTPPOST_201_FIELD_SESSION_10, session.sessionId);
					PipeWriter.writeInt(msgCommandChannel.httpRequest, ClientHTTPRequestSchema.MSG_FASTHTTPPOST_201_FIELD_PORT_1, session.port);
					PipeWriter.writeBytes(msgCommandChannel.httpRequest, ClientHTTPRequestSchema.MSG_FASTHTTPPOST_201_FIELD_HOST_2, session.hostBytes);
					
					PipeWriter.writeLong(msgCommandChannel.httpRequest, ClientHTTPRequestSchema.MSG_FASTHTTPPOST_201_FIELD_CONNECTIONID_20, session.getConnectionId());
										
					PipeWriter.writeUTF8(msgCommandChannel.httpRequest, ClientHTTPRequestSchema.MSG_FASTHTTPPOST_201_FIELD_PATH_3, route);
					
					DataOutputBlobWriter<ClientHTTPRequestSchema> hw = Pipe.outputStream(msgCommandChannel.httpRequest);
					DataOutputBlobWriter.openField(hw);
					if (null!=headers) {
						headers.write(msgCommandChannel.headerWriter.target(hw));
					}
					hw.closeHighLevelField(ClientHTTPRequestSchema.MSG_FASTHTTPPOST_201_FIELD_HEADERS_7);
					
					DataOutputBlobWriter<ClientHTTPRequestSchema> pw = Pipe.outputStream(msgCommandChannel.httpRequest);
					DataOutputBlobWriter.openField(pw);
					payload.write(pw);
					pw.closeHighLevelField(ClientHTTPRequestSchema.MSG_FASTHTTPPOST_201_FIELD_PAYLOAD_5);
					
					PipeWriter.publishWrites(msgCommandChannel.httpRequest);
					
					MsgCommandChannel.publishGo(1, msgCommandChannel.builder.netIndex(), msgCommandChannel);
					
					return true;
				}
			}
		} 
		return false;
	}

	/**
	 *
	 * @return true if msgCommandChannel.goHasRoom() <p> false otherwise
	 */
	public boolean shutdown() {
		assert(msgCommandChannel.enterBlockOk()) : "Concurrent usage error, ensure this never called concurrently";
		try {
		    if (msgCommandChannel.goHasRoom()) {            	
		    	if (null!=msgCommandChannel.goPipe) {
		    		PipeWriter.publishEOF(msgCommandChannel.goPipe);            		
		    	} else {
		    		//must find one of these outputs to shutdown
		    		if (!msgCommandChannel.sentEOF(msgCommandChannel.messagePubSub)) {
		    			if (!msgCommandChannel.sentEOF(msgCommandChannel.httpRequest)) {
		    				if (!msgCommandChannel.sentEOF(msgCommandChannel.netResponse)) {
		    					if (!msgCommandChannel.sentEOFPrivate()) {
		    						msgCommandChannel.secondShutdownMsg();
		    					}
		    				}            				
		    			}
		    		}
		    	}
		        return true;
		    } else {
		        return false;
		    }
		} finally {
		    assert(msgCommandChannel.exitBlockOk()) : "Concurrent usage error, ensure this never called concurrently";      
		}
	}
	
}
