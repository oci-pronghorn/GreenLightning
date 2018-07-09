package com.ociweb.gl.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.pronghorn.network.ClientCoordinator;
import com.ociweb.pronghorn.network.http.HeaderWritable;
import com.ociweb.pronghorn.network.schema.ClientHTTPRequestSchema;
import com.ociweb.pronghorn.pipe.DataOutputBlobWriter;
import com.ociweb.pronghorn.pipe.FieldReferenceOffsetManager;
import com.ociweb.pronghorn.pipe.Pipe;


public class HTTPRequestService {

	private final MsgCommandChannel<?> msgCommandChannel;
	private final static Logger logger = LoggerFactory.getLogger(HTTPRequestService.class);
	
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
	 * @return has room
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
		
		if (msgCommandChannel.goHasRoom() && Pipe.hasRoomForWrite(msgCommandChannel.httpRequest) ) {
								        	    
			int size = Pipe.addMsgIdx(msgCommandChannel.httpRequest, ClientHTTPRequestSchema.MSG_CLOSE_104);
			
			Pipe.addIntValue(session.sessionId, msgCommandChannel.httpRequest);	
			Pipe.addIntValue(session.port, msgCommandChannel.httpRequest);
			Pipe.addByteArray(session.hostBytes, msgCommandChannel.httpRequest);
			Pipe.confirmLowLevelWrite(msgCommandChannel.httpRequest, size);
			Pipe.publishWrites(msgCommandChannel.httpRequest);
		        		
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
		
		if (session.getConnectionId()<0) {
			final long id = ClientCoordinator.lookup(
					ClientCoordinator.lookupHostId(session.hostBytes), 
					session.port, 
					session.sessionId);
			if (id>=0) {
				session.setConnectionId(id);
			}
		}
		//////////////////////
		//get the cached connection ID so we need not deal with the host again
		/////////////////////

		if (msgCommandChannel.goHasRoom() ) {
		
			if (session.getConnectionId()<0) {
						
				if (Pipe.hasRoomForWrite(msgCommandChannel.httpRequest)) {
					
					int size = Pipe.addMsgIdx(msgCommandChannel.httpRequest, ClientHTTPRequestSchema.MSG_HTTPGET_100);
							
					Pipe.addIntValue(msgCommandChannel.builder.lookupHTTPClientPipe(session.sessionId), msgCommandChannel.httpRequest);
					Pipe.addIntValue(session.sessionId, msgCommandChannel.httpRequest);
					Pipe.addIntValue(session.port, msgCommandChannel.httpRequest);
					Pipe.addByteArray(session.hostBytes, msgCommandChannel.httpRequest);
					Pipe.addUTF8(route, msgCommandChannel.httpRequest);		    	
					
					DataOutputBlobWriter<ClientHTTPRequestSchema> hw = Pipe.outputStream(msgCommandChannel.httpRequest);
				    DataOutputBlobWriter.openField(hw);
				    if (null!=headers) {
				    	headers.write(msgCommandChannel.headerWriter.target(hw));
				    }
				    hw.closeLowLevelField();
					
				    Pipe.confirmLowLevelWrite(msgCommandChannel.httpRequest, size);
				    Pipe.publishWrites(msgCommandChannel.httpRequest);
					
					MsgCommandChannel.publishGo(1, msgCommandChannel.builder.netIndex(), msgCommandChannel);
					
					return true;
				}
			} else {
				if (Pipe.hasRoomForWrite(msgCommandChannel.httpRequest)) {
					
					int size = Pipe.addMsgIdx(msgCommandChannel.httpRequest, ClientHTTPRequestSchema.MSG_FASTHTTPGET_200);
					
					Pipe.addIntValue(msgCommandChannel.builder.lookupHTTPClientPipe(session.sessionId), msgCommandChannel.httpRequest);
					Pipe.addIntValue(session.sessionId, msgCommandChannel.httpRequest);
					Pipe.addIntValue(session.port, msgCommandChannel.httpRequest);
					Pipe.addByteArray(session.hostBytes, msgCommandChannel.httpRequest);
					Pipe.addLongValue(session.getConnectionId(), msgCommandChannel.httpRequest);
					Pipe.addUTF8(route, msgCommandChannel.httpRequest);
			
					DataOutputBlobWriter<ClientHTTPRequestSchema> hw = Pipe.outputStream(msgCommandChannel.httpRequest);
				    DataOutputBlobWriter.openField(hw);
				    if (null!=headers) {
				    	headers.write(msgCommandChannel.headerWriter.target(hw));
				    }
				    hw.closeLowLevelField();
						
				    Pipe.confirmLowLevelWrite(msgCommandChannel.httpRequest, size);
				    Pipe.publishWrites(msgCommandChannel.httpRequest);
					
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
		
		if (session.getConnectionId()<0) {
			final long id = ClientCoordinator.lookup(
					ClientCoordinator.lookupHostId(session.hostBytes), 
					session.port, 
					session.sessionId);
			if (id>=0) {
				session.setConnectionId(id);
			}
		}
		
		if (msgCommandChannel.goHasRoom() ) { 
	
			if (session.getConnectionId()<0) {
	
				if (Pipe.hasRoomForWrite(msgCommandChannel.httpRequest)) {
					
					int size = Pipe.addMsgIdx(msgCommandChannel.httpRequest, 
							      ClientHTTPRequestSchema.MSG_HTTPPOST_101);
					
					Pipe.addIntValue(msgCommandChannel.builder.lookupHTTPClientPipe(session.sessionId), msgCommandChannel.httpRequest);
					Pipe.addIntValue(session.sessionId, msgCommandChannel.httpRequest);
					Pipe.addIntValue(session.port, msgCommandChannel.httpRequest);					
					Pipe.addByteArray(session.hostBytes, msgCommandChannel.httpRequest);
					Pipe.addUTF8(route, msgCommandChannel.httpRequest);
						
					DataOutputBlobWriter<ClientHTTPRequestSchema> hw = Pipe.outputStream(msgCommandChannel.httpRequest);
					DataOutputBlobWriter.openField(hw);
					if (null!=headers) {
						headers.write(msgCommandChannel.headerWriter.target(hw));
					}
					hw.closeLowLevelField();
					
					DataOutputBlobWriter<ClientHTTPRequestSchema> pw = Pipe.outputStream(msgCommandChannel.httpRequest);
					DataOutputBlobWriter.openField(pw);
					payload.write(pw);
					pw.closeLowLevelField();
					
				    Pipe.confirmLowLevelWrite(msgCommandChannel.httpRequest, size);
				    Pipe.publishWrites(msgCommandChannel.httpRequest);
					
					MsgCommandChannel.publishGo(1, msgCommandChannel.builder.netIndex(), msgCommandChannel);
						
					return true;
				}
				
			} else {
				
				if (Pipe.hasRoomForWrite(msgCommandChannel.httpRequest)) {					

					int size = Pipe.addMsgIdx(msgCommandChannel.httpRequest, ClientHTTPRequestSchema.MSG_FASTHTTPPOST_201);
					
					Pipe.addIntValue(msgCommandChannel.builder.lookupHTTPClientPipe(session.sessionId), msgCommandChannel.httpRequest);
					Pipe.addIntValue(session.sessionId, msgCommandChannel.httpRequest);
					Pipe.addIntValue(session.port, msgCommandChannel.httpRequest);
					Pipe.addByteArray(session.hostBytes, msgCommandChannel.httpRequest);
					Pipe.addLongValue(session.getConnectionId(), msgCommandChannel.httpRequest);
						
					//path
					Pipe.addUTF8(route, msgCommandChannel.httpRequest);
						
					//headers
					DataOutputBlobWriter<ClientHTTPRequestSchema> hw = Pipe.outputStream(msgCommandChannel.httpRequest);
					DataOutputBlobWriter.openField(hw);
					if (null!=headers) {
						headers.write(msgCommandChannel.headerWriter.target(hw));
					}
					hw.closeLowLevelField();
					
					//payload
					DataOutputBlobWriter<ClientHTTPRequestSchema> pw = Pipe.outputStream(msgCommandChannel.httpRequest);
					DataOutputBlobWriter.openField(pw);
					payload.write(pw);
					pw.closeLowLevelField();
					
				    Pipe.confirmLowLevelWrite(msgCommandChannel.httpRequest, size);
				    Pipe.publishWrites(msgCommandChannel.httpRequest);
					
					MsgCommandChannel.publishGo(1, msgCommandChannel.builder.netIndex(), msgCommandChannel);
					
					return true;
				}
			}
		} 
		return false;
	}

	
	/**
	 * start shutdown of the runtime, this can be vetoed or postponed by any shutdown listeners
	 */
	public void triggerShutdownRuntime() {
		
		assert(msgCommandChannel.enterBlockOk()) : "Concurrent usage error, ensure this never called concurrently";
		try {
			msgCommandChannel.builder.triggerShutdownProcess();
		} finally {
		    assert(msgCommandChannel.exitBlockOk()) : "Concurrent usage error, ensure this never called concurrently";      
		}
	}
}
