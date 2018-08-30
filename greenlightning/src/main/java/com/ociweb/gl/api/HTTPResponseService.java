package com.ociweb.gl.api;

import com.ociweb.pronghorn.network.HTTPUtilResponse;
import com.ociweb.pronghorn.network.OrderSupervisorStage;
import com.ociweb.pronghorn.network.ServerCoordinator;
import com.ociweb.pronghorn.network.config.HTTPContentType;
import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;
import com.ociweb.pronghorn.network.config.HTTPHeaderDefaults;
import com.ociweb.pronghorn.network.config.HTTPRevisionDefaults;
import com.ociweb.pronghorn.network.http.HTTPResponseStatusCodes;
import com.ociweb.pronghorn.network.http.HTTPUtil;
import com.ociweb.pronghorn.network.http.HeaderWritable;
import com.ociweb.pronghorn.network.http.HeaderWriter;
import com.ociweb.pronghorn.network.module.AbstractAppendablePayloadResponseStage;
import com.ociweb.pronghorn.network.schema.ServerResponseSchema;
import com.ociweb.pronghorn.pipe.DataOutputBlobWriter;
import com.ociweb.pronghorn.pipe.FieldReferenceOffsetManager;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.util.Appendables;

public class HTTPResponseService {

	private final MsgCommandChannel<?> msgCommandChannel;

	public HTTPResponseService(MsgCommandChannel<?> msgCommandChannel) {
		this.msgCommandChannel = msgCommandChannel;
		msgCommandChannel.initFeatures |= MsgCommandChannel.NET_RESPONDER;
		
		
	}

	/**
	 *
	 * @param msgCommandChannel MsgCommandChannel arg used in
	 * @param queueLength int arg specifying que length
	 * @param maxMessageSize int arg specifying max message size
	 */
	public HTTPResponseService(MsgCommandChannel<?> msgCommandChannel,
			int queueLength, int maxMessageSize) {
		this.msgCommandChannel = msgCommandChannel;
		MsgCommandChannel.growCommandCountRoom(msgCommandChannel, queueLength);
		msgCommandChannel.initFeatures |= MsgCommandChannel.NET_RESPONDER;    	
		
		msgCommandChannel.pcm.ensureSize(ServerResponseSchema.class, queueLength, maxMessageSize);
	}

	/**
	 *
	 * @param messageCount int arg for number of messages
	 * @return has room for
	 */
	public boolean hasRoomFor(int messageCount) {
		return null==msgCommandChannel.goPipe || Pipe.hasRoomForWrite(msgCommandChannel.goPipe, 
		FieldReferenceOffsetManager.maxFragmentSize(Pipe.from(msgCommandChannel.goPipe))*messageCount);
	}

	/**
	 *
	 * @param requestReader HTTPFieldReader arg used in publishHTTPResponse
	 * @param statusCode int arg used in publishHTTPResponse
	 * @return HTTPResponse with given args
	 */
	public boolean publishHTTPResponse(HTTPFieldReader<?> requestReader, int statusCode) {
		assert((0 != (msgCommandChannel.initFeatures & MsgCommandChannel.NET_RESPONDER))) : "CommandChannel must be created with NET_RESPONDER flag";
		
		//logger.info("Building response for connection {} sequence {} ",w.getConnectionId(),w.getSequenceCode());
		
		return publishHTTPResponse(requestReader.getConnectionId(), requestReader.getSequenceCode(),
				statusCode,false,null,Writable.NO_OP); //no type and no body so use null
	}

	/**
	 *
	 * @param requestReader HTTPFieldReader arg used in publishHTTPResponse
	 * @param statusCode int used as arg in publishHTTPResponse
	 * @param contentType HTTPContentType used as arg in publishHTTPResponse
	 * @param writable Writable used as arg in publishHTTPResponse
	 * @return HTTPResponse with given args
	 */
	public boolean publishHTTPResponse(HTTPFieldReader<?> requestReader,
			int statusCode,
		    HTTPContentType contentType,
		   	Writable writable) {
		assert((0 != (msgCommandChannel.initFeatures & MsgCommandChannel.NET_RESPONDER))) : "CommandChannel must be created with NET_RESPONDER flag";
		
		return publishHTTPResponse(requestReader.getConnectionId(), requestReader.getSequenceCode(),
									statusCode, false, contentType, writable);
	}

	/**
	 *
	 * @param requestReader HTTPFieldReader used as arg in publishHTTPResponse
	 * @param statusCode int used as arg in publishHTTPResponse
	 * @param hasContinuation boolean used as arg in publishHTTPResponse
	 * @param contentType HTTPContentType used as arg in publishHTTPResponse
	 * @param writable Writable used as arg in publishHTTPResponse
	 * @return HTTPResponse with given args
	 */
	public boolean publishHTTPResponse(HTTPFieldReader<?> requestReader,
			   int statusCode, boolean hasContinuation,
			   HTTPContentType contentType,
			   Writable writable) {
		assert((0 != (msgCommandChannel.initFeatures & MsgCommandChannel.NET_RESPONDER))) : "CommandChannel must be created with NET_RESPONDER flag";
		
		return publishHTTPResponse(requestReader.getConnectionId(), requestReader.getSequenceCode(),
				                statusCode, hasContinuation, contentType, writable);	
	}

	/**
	 *
	 * @param connectionId long val used as arg in publishHTTPResponse
	 * @param sequenceCode long val used as arg in publishHTTPResponse
	 * @param statusCode int val used as arg in publishHTTPResponse
	 * @return HTTPResponse with give args
	 */
	public boolean publishHTTPResponse(long connectionId, long sequenceCode, int statusCode) {
		return publishHTTPResponse(connectionId, sequenceCode, statusCode, false, null, Writable.NO_OP);
	}

	/**
	 *
	 * @param connectionId long val used as arg in @link <Pipe.addLongValue> //better to write like this?
	 * @param sequenceCode long val used as arg in Pipe.addIntValue  //or this?
	 * @param statusCode int val used as arg in outputField.openStream
	 * @param hasContinuation boolean val used to determine if msgCommandChannel.lastResponseWriterFinished = 0 || 1
	 * @param contentType HTTPContentType used as arg in outputField.openStream
	 * @param writable Writable used to write outputStream
	 * @return <code>false</code> if !Pipe.hasRoomForWrite else <code>true</code>
	 */
	public boolean publishHTTPResponse(long connectionId, long sequenceCode, 
							            int statusCode, 
							            boolean hasContinuation,
							            HTTPContentType contentType,
							            Writable writable) {
		assert(null!=writable);
		assert((0 != (msgCommandChannel.initFeatures & MsgCommandChannel.NET_RESPONDER))) : "CommandChannel must be created with NET_RESPONDER flag";
		
		final int sequenceNo = 0xFFFFFFFF & (int)sequenceCode;
		final int trackIdx   = 0xFFFFFFFF & (int)(sequenceCode>>32);
		final int isClosed   = OrderSupervisorStage.CLOSE_CONNECTION_MASK & (int)(sequenceCode>>32);
		
		assert(1==msgCommandChannel.lastResponseWriterFinished) : "Previous write was not ended can not start another.";
			
		Pipe<ServerResponseSchema> pipe = msgCommandChannel.netResponse.length>1 ? msgCommandChannel.netResponse[trackIdx] : msgCommandChannel.netResponse[0];
		
		//logger.info("try new publishHTTPResponse "+pipe);
		if (!Pipe.hasRoomForWrite(pipe, 
				2*Pipe.sizeOf(pipe, ServerResponseSchema.MSG_TOCHANNEL_100))) {
			return false;
		}		
		//simple check to ensure we have room.
		assert(Pipe.workingHeadPosition(pipe)<(Pipe.tailPosition(pipe)+ pipe.sizeOfSlabRing  /*    pipe.slabMask*/  )) : "Working position is now writing into published(unreleased) tail "+
		Pipe.workingHeadPosition(pipe)+"<"+Pipe.tailPosition(pipe)+"+"+pipe.sizeOfSlabRing /*pipe.slabMask*/+" total "+((Pipe.tailPosition(pipe)+pipe.slabMask));
		
		
		///////////////////////////////////////
		//message 1 which contains the headers
		//////////////////////////////////////		
		HTTPUtilResponse.holdEmptyBlock(msgCommandChannel.data, connectionId, sequenceNo, pipe);
		//HTTPUtilResponse.holdEmptyBlock(msgCommandChannel.data,pipe);
		
		
		//check again because we have taken 2 spots now
		assert(Pipe.workingHeadPosition(pipe)<(Pipe.tailPosition(pipe)+ pipe.sizeOfSlabRing  /*    pipe.slabMask*/  )) : "Working position is now writing into published(unreleased) tail "+
		Pipe.workingHeadPosition(pipe)+"<"+Pipe.tailPosition(pipe)+"+"+pipe.sizeOfSlabRing /*pipe.slabMask*/+" total "+((Pipe.tailPosition(pipe)+pipe.slabMask));
		
		//////////////////////////////////////////
		//begin message 2 which contains the body
		//////////////////////////////////////////
		
		Pipe.addMsgIdx(pipe, ServerResponseSchema.MSG_TOCHANNEL_100);
		Pipe.addLongValue(connectionId, pipe);
		Pipe.addIntValue(sequenceNo, pipe);
		
		NetResponseWriter outputStream = (NetResponseWriter)Pipe.outputStream(pipe);
		
		int context;
		if (hasContinuation) {
			context = 0;
			msgCommandChannel.lastResponseWriterFinished = 0;
		} else {
			context = ServerCoordinator.END_RESPONSE_MASK;
			msgCommandChannel.lastResponseWriterFinished = 1;	
			if (0!=isClosed) {
				//only do this when we received a close from client
				context |= ServerCoordinator.CLOSE_CONNECTION_MASK;
			}
		}		
		
		
		//NB: context passed in here is looked at to know if this is END_RESPONSE and if so
		//then the length is added if not then the header will designate chunked.
		outputStream.openField(statusCode, context, contentType);
		writable.write(outputStream); 
		
		if (hasContinuation) {
			// for chunking we must end this block			
			outputStream.write(MsgCommandChannel.RETURN_NEWLINE);
		}
		
		assert(isValidContent(contentType,outputStream)) : "content type is not matching payload";
				
		
		outputStream.publishWithHeader(
				msgCommandChannel.data.block1HeaderBlobPosition, 
				msgCommandChannel.data.block1PositionOfLen); //closeLowLevelField and publish 
		
		return true;
				
	}

	private boolean isValidContent(HTTPContentType contentType, NetResponseWriter outputStream) {
		StringBuilder target = new StringBuilder();
		
		if (HTTPContentTypeDefaults.JSON == contentType ||
			HTTPContentTypeDefaults.TXT == contentType) {		
			//simple check to make sure JSON starts with text not the length
			outputStream.debugAsUTF8(target);
			if (target.length()>0) {
				if (target.charAt(0)<32) {
					return false;
				}
				if (target.length()>1) {
					if (target.charAt(1)<32) {
						return false;
					}	
				}
			}
		}
		
		return true;
	}

	/**
	 *
	 * @param reqeustReader HTTPFieldReader<?> arg used in publishHTTPResponse
	 * @param headers HeaderWritable arg used in publishHTTPResponse
	 * @param writable Writable arg used in publishHTTPResponse
	 * @return publishHTTPResponse(reqeustReader.getConnectionId (), reqeustReader.getSequenceCode(),
	 * 				false, headers, 200, writable)
	 */
	public boolean publishHTTPResponse(HTTPFieldReader<?> reqeustReader, 
	           HeaderWritable headers, HTTPContentType contentType, Writable writable) {
		return publishHTTPResponse(reqeustReader.getConnectionId(), reqeustReader.getSequenceCode(),
				200, false, headers, contentType, writable);
	}

	/**
	 *
	 * @param connectionId long arg used in msgCommandChannel.holdEmptyBlock and Pipe.addLongValue
	 * @param sequenceCode long arg set as final int sequenceNo and parallelIndex
	 * @param statusCode int arg used to display status code to user
	 * @param hasContinuation boolean used to determine of msgCommandChannel.lastResponseWriterFinished or outputStream.write
	 * @param headers HeaderWritable arg. If !null write(msgCommandChannel.headerWriter.target(outputStream))
	 * @param writable Writable arg used to write outputStream
	 * @return if !Pipe.hasRoomForWrite(pipe) return false else return true
	 */
	public boolean publishHTTPResponse(long connectionId, long sequenceCode, 
							           int statusCode, boolean hasContinuation, HeaderWritable headers,
							           HTTPContentType contentType, Writable writable) {
		assert((0 != (msgCommandChannel.initFeatures & MsgCommandChannel.NET_RESPONDER))) : "CommandChannel must be created with NET_RESPONDER flag";
		
		final int sequenceNo = 0xFFFFFFFF & (int)sequenceCode;
		final int parallelIndex = 0xFFFFFFFF & (int)(sequenceCode>>32);
		
		assert(1==msgCommandChannel.lastResponseWriterFinished) : "Previous write was not ended can not start another.";
		
		Pipe<ServerResponseSchema> pipe = msgCommandChannel.netResponse.length>1 ? msgCommandChannel.netResponse[parallelIndex] : msgCommandChannel.netResponse[0];
		
		if (!Pipe.hasRoomForWrite(pipe)) {
			return false;
		}		
						
		///////////////////////////////////////
		//message 1 which contains the headers
		//////////////////////////////////////
		HTTPUtilResponse.holdEmptyBlock(msgCommandChannel.data, connectionId, sequenceNo, pipe);
		
		//////////////////////////////////////////
		//begin message 2 which contains the body
		//////////////////////////////////////////
		
		Pipe.addMsgIdx(pipe, ServerResponseSchema.MSG_TOCHANNEL_100);
		Pipe.addLongValue(connectionId, pipe);
		Pipe.addIntValue(sequenceNo, pipe);	
		
		NetResponseWriter outputStream = (NetResponseWriter)Pipe.outputStream(pipe);
		
		int context;
		if (hasContinuation) {
			context = 0;
			msgCommandChannel.lastResponseWriterFinished = 0;
		} else {
			context = ServerCoordinator.END_RESPONSE_MASK;
			msgCommandChannel.lastResponseWriterFinished = 1;	
		}	
		
		//NB: context passed in here is looked at to know if this is END_RESPONSE and if so
		//then the length is added if not then the header will designate chunked.
		outputStream.openField(statusCode, context, contentType);
		writable.write(outputStream); 
		
		if (hasContinuation) {
			// for chunking we must end this block			
			outputStream.write(MsgCommandChannel.RETURN_NEWLINE);
		}
		
		int len = NetResponseWriter.closeLowLevelField(outputStream); //end of writing the payload    	
		
		Pipe.addIntValue(context, outputStream.getPipe());  //real context    	
		Pipe.confirmLowLevelWrite(outputStream.getPipe());
		   	
		////////////////////Write the header
		
		HTTPUtilResponse data = msgCommandChannel.data;
				
		HTTPUtilResponse.openToEmptyBlock(data, outputStream);
		
		outputStream.write(HTTPRevisionDefaults.HTTP_1_1.getBytes());				
		outputStream.write(HTTPResponseStatusCodes.codes[statusCode]); //this is " 200 OK\r\n" for most calls

		HeaderWriter headerWriter = msgCommandChannel.headerWriter.target(outputStream);
		if (null!=headers) {
			headers.write(headerWriter); //NOTE: using content_length, connection or status are all discouraged here..
		}
		headerWriter.write(HTTPHeaderDefaults.CONTENT_LENGTH, len);
		outputStream.write("\r\n".getBytes());
		
		//outputStream.debugAsUTF8();
		
		HTTPUtilResponse.finalizeLengthOfFirstBlock(data, outputStream);
		
		//now publish both header and payload
		Pipe.publishWrites(outputStream.getPipe());
		
		return true;
	}

	/**
	 *
	 * @param w HTTPFieldReader arg used in publishHTTPResponseContinuation
	 * @param hasContinuation boolean arg used in publishHTTPResponseContinuation
	 * @param writable Writable arg used in publishHTTPResponseContinuation
	 * @return publishHTTPResponseContinuation(w.getConnectionId(),w.getSequenceCode(), hasContinuation, writable)
	 */
	public boolean publishHTTPResponseContinuation(HTTPFieldReader<?> w, 
			boolean hasContinuation, Writable writable) {
		return publishHTTPResponseContinuation(w.getConnectionId(),w.getSequenceCode(), hasContinuation, writable);
	}

	/**
	 *
	 * @param connectionId long value used as arg for msgCommandChannel.holdEmptyBlock and Pipe.addLongValue
	 * @param sequenceCode long value used as arg in Pipe.addIntValue and msgCommandChannel.holdEmptyBlock
	 * @param hasContinuation boolean used to make msgCommandChannel.lastResponseWriterFinished 0 if <code>true</code> else 1
	 * @param writable Writable used to wrote outputStream
	 * @return <code>false</code> if !Pipe.hasRoomForWrite(pipe) else <code>true</code>
	 */
	public boolean publishHTTPResponseContinuation(long connectionId, long sequenceCode, 
			   boolean hasContinuation, Writable writable) {
		assert((0 != (msgCommandChannel.initFeatures & MsgCommandChannel.NET_RESPONDER))) : "CommandChannel must be created with NET_RESPONDER flag";
		
		final int sequenceNo = 0xFFFFFFFF & (int)sequenceCode;
		final int parallelIndex = 0xFFFFFFFF & (int)(sequenceCode>>32);		
		
		assert(0==msgCommandChannel.lastResponseWriterFinished) : "Unable to find write in progress, nothing to continue with";
		
		Pipe<ServerResponseSchema> pipe = msgCommandChannel.netResponse.length>1 ? msgCommandChannel.netResponse[parallelIndex] : msgCommandChannel.netResponse[0];
		
		//logger.trace("calling publishHTTPResponseContinuation");
		
		if (!Pipe.hasRoomForWrite(pipe)) {
			return false;
		}
		
		
		///////////////////////////////////////
		//message 1 which contains the chunk length
		//////////////////////////////////////		
		HTTPUtilResponse.holdEmptyBlock(msgCommandChannel.data, connectionId, sequenceNo, pipe);
		
		///////////////////////////////////////
		//message 2 which contains the chunk
		//////////////////////////////////////	
		
		Pipe.addMsgIdx(pipe, ServerResponseSchema.MSG_TOCHANNEL_100);
		Pipe.addLongValue(connectionId, pipe);
		Pipe.addIntValue(sequenceNo, pipe);	
		NetResponseWriter outputStream = (NetResponseWriter)Pipe.outputStream(pipe);
		
		outputStream.openField(hasContinuation? 0: ServerCoordinator.END_RESPONSE_MASK);
		msgCommandChannel.lastResponseWriterFinished = hasContinuation ? 0 : 1;		
		
		writable.write(outputStream); 
		//this is not the end of the data so we must close this block
		outputStream.write(MsgCommandChannel.RETURN_NEWLINE);
		
		
		if (1 == msgCommandChannel.lastResponseWriterFinished) {			
			//this is the end of the data, we must close the block
			//and add the zero trailer
			
			//this adds 3, note the publishWithChunkPrefix also takes this into account
			Appendables.appendHexDigitsRaw(outputStream, 0);
			outputStream.write(HTTPUtil.RETURN_NEWLINE);
						
			//TODO: add trailing headers here. (no request for this feature yet)
			
			outputStream.write(HTTPUtil.RETURN_NEWLINE);
		
			
		}
		
		outputStream.publishWithChunkPrefix(msgCommandChannel.data.block1HeaderBlobPosition, msgCommandChannel.data.block1PositionOfLen);
		
		return true;
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
