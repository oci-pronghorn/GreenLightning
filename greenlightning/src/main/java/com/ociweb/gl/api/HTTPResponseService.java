package com.ociweb.gl.api;

import com.ociweb.pronghorn.network.HTTPUtilResponse;
import com.ociweb.pronghorn.network.OrderSupervisorStage;
import com.ociweb.pronghorn.network.ServerCoordinator;
import com.ociweb.pronghorn.network.config.HTTPContentType;
import com.ociweb.pronghorn.network.config.HTTPHeaderDefaults;
import com.ociweb.pronghorn.network.config.HTTPRevisionDefaults;
import com.ociweb.pronghorn.network.http.HTTPResponseStatusCodes;
import com.ociweb.pronghorn.network.http.HTTPUtil;
import com.ociweb.pronghorn.network.http.HeaderWritable;
import com.ociweb.pronghorn.network.http.HeaderWriter;
import com.ociweb.pronghorn.network.http.SequenceValidator;
import com.ociweb.pronghorn.network.schema.ServerResponseSchema;
import com.ociweb.pronghorn.pipe.FieldReferenceOffsetManager;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.util.Appendables;

public class HTTPResponseService {

	private static final int TWO_RESPONSE_BLOCKS_SIZE = 2*Pipe.sizeOf(ServerResponseSchema.instance, ServerResponseSchema.MSG_TOCHANNEL_100);
	private final MsgCommandChannel<?> msgCommandChannel;
	public static final byte[] SERVER_HEADER_NAME = "GreenLightning".getBytes();
	private static final int minHeader = 128;
	private transient SequenceValidator validator; //will be null unless assertions are on.
	
	private boolean setupValidator() {
		validator = new SequenceValidator();
		return true;
	}
	
	public HTTPResponseService(MsgCommandChannel<?> msgCommandChannel) {
		this.msgCommandChannel = msgCommandChannel;
		msgCommandChannel.initFeatures |= MsgCommandChannel.NET_RESPONDER;		
		msgCommandChannel.pcm.ensureSize(ServerResponseSchema.class, 1, minHeader);
		assert(setupValidator());
		
		
		//TODO: need to write response type and JSON back to HTTPRouterStageConfig....
		
		//TODO: where does th router stage config live?
	//	msgCommandChannel.builder.getHTTPServerConfig().
		
		
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
		msgCommandChannel.pcm.ensureSize(ServerResponseSchema.class, queueLength, Math.max(minHeader, maxMessageSize));
		assert(setupValidator());
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

	
	
	public void logTelemetrySnapshot() {
		msgCommandChannel.logTelemetrySnapshot();
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

	public boolean publishHTTPResponse(long connectionId, long sequenceCode, 
            int statusCode, 
            HTTPContentType contentType,
            Writable writable) {
		return publishHTTPResponse(connectionId, sequenceCode, statusCode, false, contentType, writable);
	}
	/**
	 *
	 * @param connectionId long val used as arg in Pipe.addLongValue
	 * @param sequenceCode long val used as arg in Pipe.addIntValue 
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
			
		assert(null != msgCommandChannel.netResponse) : "no target pipe for response at time of write.";
		Pipe<ServerResponseSchema> pipe = msgCommandChannel.netResponse.length>1 ? msgCommandChannel.netResponse[trackIdx] : msgCommandChannel.netResponse[0];
		
		//logger.info("try new publishHTTPResponse "+pipe);
		if (!Pipe.hasRoomForWrite(pipe, 2*Pipe.sizeOf(pipe, ServerResponseSchema.MSG_TOCHANNEL_100))) {
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

		outputStream.publishWithHeader(
				msgCommandChannel.data.block1HeaderBlobPosition, 
				msgCommandChannel.data.block1PositionOfLen); //closeLowLevelField and publish 
		
		return true;
				
	}


	/**
	 *
	 * @param reqeustReader HTTPFieldReader arg used in publishHTTPResponse
	 * @param headers HeaderWritable arg used in publishHTTPResponse
	 * @param writable Writable arg used in publishHTTPResponse
	 * @return publishHTTPResponse(reqeustReader.getConnectionId (), reqeustReader.getSequenceCode(),
	 * 				false, headers, 200, writable)
	 */
	public boolean publishHTTPResponse(HTTPFieldReader reqeustReader, 
	           HeaderWritable headers, HTTPContentType contentType, Writable writable) {
		return publishHTTPResponse(reqeustReader.getConnectionId(), reqeustReader.getSequenceCode(),
				200, false, headers, contentType, writable);
	}

	public boolean publishHTTPResponse(HTTPFieldReader<?> reqeustReader, 
	           HTTPContentType contentType, Writable writable) {
		return publishHTTPResponse(reqeustReader.getConnectionId(), reqeustReader.getSequenceCode(),
				200, false, null, contentType, writable);
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
		
		final int parallelIndex = 0xFFFFFFFF & (int)(sequenceCode>>32);
		
		assert(1==msgCommandChannel.lastResponseWriterFinished) : "Previous write was not ended can not start another.";
		
		Pipe<ServerResponseSchema> pipe = msgCommandChannel.netResponse.length>1 ? msgCommandChannel.netResponse[parallelIndex] : msgCommandChannel.netResponse[0];
		
		//header and pay load sent as 2 writes
		if (!Pipe.hasRoomForWrite(pipe, TWO_RESPONSE_BLOCKS_SIZE)) {
			return false;
		}		
		final int sequenceNo = 0xFFFFFFFF & (int)sequenceCode;
						
		assert(validator.isValidSequence(connectionId, sequenceCode));
		
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
		
		int len = writePayload(hasContinuation, writable, pipe, outputStream);
		   	
		writeHeaderAndPublish(statusCode, headers, contentType, pipe, outputStream, len, msgCommandChannel);
		
		return true;
	}

	private int writePayload(boolean hasContinuation, Writable writable, Pipe<ServerResponseSchema> pipe,
			NetResponseWriter outputStream) {
		int context;
		if (!hasContinuation) {
			context = ServerCoordinator.END_RESPONSE_MASK;
			msgCommandChannel.lastResponseWriterFinished = 1;
			outputStream.openField();
			writable.write(outputStream);
		} else {
			context = 0;
			msgCommandChannel.lastResponseWriterFinished = 0;
			outputStream.openField();
			writable.write(outputStream);
			// for chunking we must end this block			
			outputStream.write(MsgCommandChannel.RETURN_NEWLINE);
		}	
		
		int len = NetResponseWriter.closeLowLevelField(outputStream); //end of writing the payload    	
		
		Pipe.addIntValue(context, pipe);  //real context    	
		Pipe.confirmLowLevelWrite(pipe);
		return len;
	}

	private static void writeHeaderAndPublish(int statusCode, HeaderWritable headers, HTTPContentType contentType,
			Pipe<ServerResponseSchema> pipe, NetResponseWriter outputStream, int len, MsgCommandChannel<?> msgCommandChannel) {
		////////////////////Write the header
		
		HTTPUtilResponse.openToEmptyBlock(msgCommandChannel.data, outputStream);		
		outputStream.write(HTTPRevisionDefaults.HTTP_1_1.getBytes());				
		outputStream.write(HTTPResponseStatusCodes.codes[statusCode]); //this is " 200 OK\r\n" for most calls

		writeDateTimeHeader(outputStream);
		writeHeadersWithHeaderWriter(headers, contentType, outputStream, len, msgCommandChannel.headerWriter.target(outputStream));
		
		HTTPUtilResponse.finalizeLengthOfFirstBlock(msgCommandChannel.data, outputStream);
		
		//now publish both header and payload
		Pipe.publishWrites(pipe);
	}

	private static void writeDateTimeHeader(NetResponseWriter outputStream) {
		///////////////////////
		//write date GC free direct copy
		///////////////////////
		outputStream.write(HTTPHeaderDefaults.DATE.rootBytes());
		outputStream.dateFormatter.write(System.currentTimeMillis(), outputStream);
		outputStream.writeByte('\r');
		outputStream.writeByte('\n');
	}

	private static void writeHeadersWithHeaderWriter(HeaderWritable headers, HTTPContentType contentType,
			NetResponseWriter outputStream, int len, HeaderWriter headerWriter) {
		if (null!=headers) {
			headers.write(headerWriter); //NOTE: using content_length, connection or status are all discouraged here..
		}
		
		headerWriter.writeUTF8(HTTPHeaderDefaults.SERVER, SERVER_HEADER_NAME);
		headerWriter.writeUTF8(HTTPHeaderDefaults.CONTENT_TYPE, contentType.getBytes());
	
		headerWriter.write(HTTPHeaderDefaults.CONTENT_LENGTH, len);
		outputStream.writeByte('\r');
		outputStream.writeByte('\n');
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
		//header and pay load sent as 2 writes
		if (!Pipe.hasRoomForWrite(pipe, 2*Pipe.sizeOf(pipe, ServerResponseSchema.MSG_TOCHANNEL_100))) {
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

	/**
	 *
	 * @param messageCount int arg for number of messages
	 * @return has room for
	 */
	public boolean hasRoomFor(int messageCount) {
		
		boolean goHasRoom = null==msgCommandChannel.goPipe 
				|| Pipe.hasRoomForWrite(msgCommandChannel.goPipe, 
						FieldReferenceOffsetManager.maxFragmentSize(Pipe.from(msgCommandChannel.goPipe))*messageCount);
		
		if (goHasRoom) {
			//since we do not know the exact publication we must check 
			//all of them and ensure they each have room.
			int i = msgCommandChannel.netResponse.length;
			while (--i >= 0) {
				Pipe<ServerResponseSchema> dataPipe = msgCommandChannel.netResponse[i];
				if (!Pipe.hasRoomForWrite(dataPipe, FieldReferenceOffsetManager.maxFragmentSize(Pipe.from(dataPipe))*messageCount)) {
					return false;
				}		
			}
			return goHasRoom;
		} else {
			return false;//go had no room so we stopped early
		}
		
	}
	
	
	public int maxVarLength() {
		//NOTE: we know that all msgCommandChannel.netResponse pipes used the same config so we can just pick the first
		assert(allSameConfig(msgCommandChannel.netResponse)) : "all the configs should have been the same";	
		return msgCommandChannel.netResponse[0].maxVarLen;
	}

	private boolean allSameConfig(Pipe<ServerResponseSchema>[] pipes) {
		int i = pipes.length;
		while (--i>=0) {
			if (pipes[i].config() != pipes[0].config()) {
				return false;
			}
		}
		return true;
	}
}
