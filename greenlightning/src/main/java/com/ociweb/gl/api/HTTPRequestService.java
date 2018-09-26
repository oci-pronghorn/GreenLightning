package com.ociweb.gl.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.pronghorn.network.ClientCoordinator;
import com.ociweb.pronghorn.network.SSLUtil;
import com.ociweb.pronghorn.network.http.HeaderWritable;
import com.ociweb.pronghorn.network.schema.ClientHTTPRequestSchema;
import com.ociweb.pronghorn.pipe.DataOutputBlobWriter;
import com.ociweb.pronghorn.pipe.FieldReferenceOffsetManager;
import com.ociweb.pronghorn.pipe.Pipe;


public class HTTPRequestService {

	public final MsgCommandChannel<?> msgCommandChannel;
	private final static Logger logger = LoggerFactory.getLogger(HTTPRequestService.class);
	
	public HTTPRequestService(MsgCommandChannel<?> msgCommandChannel) {
		this.msgCommandChannel = msgCommandChannel;	
		
		if (msgCommandChannel.builder.getHTTPClientConfig().isTLS()) {			
			msgCommandChannel.pcm.ensureSize(ClientHTTPRequestSchema.class, 4, SSLUtil.MinTLSBlock);
		}
		
		msgCommandChannel.initFeatures |= MsgCommandChannel.NET_REQUESTER;
	}
	
	public HTTPRequestService(MsgCommandChannel<?> msgCommandChannel, int queueLength, int maxMessageSize) {
		this.msgCommandChannel = msgCommandChannel;
		
		if (msgCommandChannel.builder.getHTTPClientConfig().isTLS()) {
			//TLS must have at lest 33305
			
			int tlsBody = Math.max(SSLUtil.MinTLSBlock, maxMessageSize);
			int tlsLen =  Math.max(Math.min(queueLength, (1<<27)/tlsBody),4);
			msgCommandChannel.pcm.ensureSize(ClientHTTPRequestSchema.class, tlsLen, tlsBody);
		} else {
			msgCommandChannel.pcm.ensureSize(ClientHTTPRequestSchema.class, queueLength, maxMessageSize);
		}
		
		MsgCommandChannel.growCommandCountRoom(msgCommandChannel, queueLength);
		msgCommandChannel.initFeatures |= MsgCommandChannel.NET_REQUESTER;
			
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
								        	    
			int size = Pipe.addMsgIdx(msgCommandChannel.httpRequest, ClientHTTPRequestSchema.MSG_CLOSECONNECTION_104);

			Pipe.addIntValue(session.sessionId, msgCommandChannel.httpRequest);	
			Pipe.addIntValue(session.port, msgCommandChannel.httpRequest);
			Pipe.addIntValue(session.hostId, msgCommandChannel.httpRequest);
			Pipe.addLongValue(session.getConnectionId(), msgCommandChannel.httpRequest);
					
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

	public boolean httpDelete(ClientHostPortInstance session, CharSequence route) {
		return httpGet(session,route,null);
	}
	
	public boolean httpHead(ClientHostPortInstance session, CharSequence route) {
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
		return httpWithoutPayload(session, route, headers, ClientHTTPRequestSchema.MSG_GET_200);
	}

	public boolean httpDelete(ClientHostPortInstance session, CharSequence route, HeaderWritable headers) {
		return httpWithoutPayload(session, route, headers, ClientHTTPRequestSchema.MSG_DELETE_203);
	}
	
	public boolean httpHead(ClientHostPortInstance session, CharSequence route, HeaderWritable headers) {
		return httpWithoutPayload(session, route, headers, ClientHTTPRequestSchema.MSG_HEAD_202);
	}
	
	private boolean httpWithoutPayload(ClientHostPortInstance session, CharSequence route, HeaderWritable headers,
			int verb) {
		assert(msgCommandChannel.builder.getHTTPClientConfig() != null);
		assert((msgCommandChannel.initFeatures & MsgCommandChannel.NET_REQUESTER)!=0) : "must turn on NET_REQUESTER to use this method";
		
		session.setConnectionId(ClientCoordinator.lookup(
				session.hostId, 
				session.port, 
				session.sessionId));
		
		//////////////////////
		//get the cached connection ID so we need not deal with the host again
		/////////////////////

		if (msgCommandChannel.goHasRoom() ) {
					
				if (Pipe.hasRoomForWrite(msgCommandChannel.httpRequest)) {
					
					int size = Pipe.addMsgIdx(msgCommandChannel.httpRequest, verb);
					
					Pipe.addIntValue(session.sessionId, msgCommandChannel.httpRequest);
					Pipe.addIntValue(session.port, msgCommandChannel.httpRequest);
					Pipe.addIntValue(session.hostId, msgCommandChannel.httpRequest);
					Pipe.addLongValue(session.getConnectionId(), msgCommandChannel.httpRequest);
					Pipe.addIntValue(ClientHostPortInstance.getTargetResponsePipeIdx(session), msgCommandChannel.httpRequest);
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
		return false;
	}

	public boolean httpPost(ClientHostPortInstance session, CharSequence route, Writable payload) {
		return httpPost(session, route, null, payload);
	}

	public boolean httpPut(ClientHostPortInstance session, CharSequence route, Writable payload) {
		return httpPut(session, route, null, payload);
	}
	
	public boolean httpPatch(ClientHostPortInstance session, CharSequence route, Writable payload) {
		return httpPatch(session, route, null, payload);
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
		return httpWithPayload(session, route, headers, payload, ClientHTTPRequestSchema.MSG_POST_201);
	}

	public boolean httpPut(ClientHostPortInstance session, CharSequence route, HeaderWritable headers, Writable payload) {
		return httpWithPayload(session, route, headers, payload, ClientHTTPRequestSchema.MSG_PUT_204);
	}
	
	public boolean httpPatch(ClientHostPortInstance session, CharSequence route, HeaderWritable headers, Writable payload) {
		return httpWithPayload(session, route, headers, payload, ClientHTTPRequestSchema.MSG_PATCH_205);
	}
	
	private boolean httpWithPayload(ClientHostPortInstance session, CharSequence route, HeaderWritable headers,
			Writable payload, int verb) {
		assert((msgCommandChannel.initFeatures & MsgCommandChannel.NET_REQUESTER)!=0) : "must turn on NET_REQUESTER to use this method";
		assert(null!=session);
		
		session.setConnectionId(ClientCoordinator.lookup(
				session.hostId, 
				session.port, 
				session.sessionId));

		if (msgCommandChannel.goHasRoom() ) { 
	
				if (Pipe.hasRoomForWrite(msgCommandChannel.httpRequest)) {					

					int size = Pipe.addMsgIdx(msgCommandChannel.httpRequest, verb);			
			
					Pipe.addIntValue(session.sessionId, msgCommandChannel.httpRequest);
					Pipe.addIntValue(session.port, msgCommandChannel.httpRequest);
					Pipe.addIntValue(session.hostId, msgCommandChannel.httpRequest);
					Pipe.addLongValue(session.getConnectionId(), msgCommandChannel.httpRequest);
					Pipe.addIntValue(ClientHostPortInstance.getTargetResponsePipeIdx(session), msgCommandChannel.httpRequest); 			 
						
					//path
					Pipe.addUTF8(route, msgCommandChannel.httpRequest);
						
					//headers
					DataOutputBlobWriter<ClientHTTPRequestSchema> hw = Pipe.openOutputStream(msgCommandChannel.httpRequest);
				    if (null!=headers) {
						headers.write(msgCommandChannel.headerWriter.target(hw));
					}
					hw.closeLowLevelField();
					
					//payload
					DataOutputBlobWriter<ClientHTTPRequestSchema> pw = Pipe.openOutputStream(msgCommandChannel.httpRequest);
					payload.write(pw);
					pw.closeLowLevelField();
					
				    Pipe.confirmLowLevelWrite(msgCommandChannel.httpRequest, size);
				    Pipe.publishWrites(msgCommandChannel.httpRequest);
					
					MsgCommandChannel.publishGo(1, msgCommandChannel.builder.netIndex(), msgCommandChannel);
					
					return true;
				}
			
		} 
		return false;
	}

	
	/**
	 * start shutdown of the runtime, this can be vetoed or postponed by any shutdown listeners
	 */
	public void requestShutdown() {
		
		assert(msgCommandChannel.enterBlockOk()) : "Concurrent usage error, ensure this never called concurrently";
		try {
			msgCommandChannel.builder.requestShutdown();
		} finally {
		    assert(msgCommandChannel.exitBlockOk()) : "Concurrent usage error, ensure this never called concurrently";      
		}
	}
}
