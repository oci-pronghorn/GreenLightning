package com.ociweb.gl.api;

import com.ociweb.pronghorn.network.config.HTTPContentType;
import com.ociweb.pronghorn.network.config.HTTPRevisionDefaults;
import com.ociweb.pronghorn.network.module.AbstractAppendablePayloadResponseStage;
import com.ociweb.pronghorn.network.schema.ServerResponseSchema;
import com.ociweb.pronghorn.pipe.DataOutputBlobWriter;
import com.ociweb.pronghorn.pipe.FieldReferenceOffsetManager;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeWriter;
import com.ociweb.pronghorn.util.Appendables;

public class HTTPResponseService {

	private final MsgCommandChannel<?> msgCommandChannel;
	
	public HTTPResponseService(MsgCommandChannel<?> msgCommandChannel) {
		this.msgCommandChannel = msgCommandChannel;
		msgCommandChannel.initFeatures |= MsgCommandChannel.NET_RESPONDER;
	}
	
	public HTTPResponseService(MsgCommandChannel<?> msgCommandChannel,
			int queueLength, int maxMessageSize) {
		this.msgCommandChannel = msgCommandChannel;
		MsgCommandChannel.growCommandCountRoom(msgCommandChannel, queueLength);
		msgCommandChannel.initFeatures |= MsgCommandChannel.NET_RESPONDER;    	
		
		msgCommandChannel.pcm.ensureSize(ServerResponseSchema.class, queueLength, maxMessageSize);
	}
	
	public boolean hasRoomFor(int messageCount) {
		return null==msgCommandChannel.goPipe || Pipe.hasRoomForWrite(msgCommandChannel.goPipe, 
		FieldReferenceOffsetManager.maxFragmentSize(Pipe.from(msgCommandChannel.goPipe))*messageCount);
	}
	

	public boolean publishHTTPResponse(HTTPFieldReader<?> reqeustReader, int statusCode) {
		assert((0 != (msgCommandChannel.initFeatures & MsgCommandChannel.NET_RESPONDER))) : "CommandChannel must be created with NET_RESPONDER flag";
		
		//logger.info("Building response for connection {} sequence {} ",w.getConnectionId(),w.getSequenceCode());
		
		return publishHTTPResponse(reqeustReader.getConnectionId(), reqeustReader.getSequenceCode(),
				statusCode,false,null,Writable.NO_OP); //no type and no body so use null
	}
	
	public boolean publishHTTPResponse(HTTPFieldReader<?> reqeustReader, 
			int statusCode,
		    HTTPContentType contentType,
		   	Writable writable) {
		assert((0 != (msgCommandChannel.initFeatures & MsgCommandChannel.NET_RESPONDER))) : "CommandChannel must be created with NET_RESPONDER flag";
		
		return publishHTTPResponse(reqeustReader.getConnectionId(), reqeustReader.getSequenceCode(),
									statusCode, false, contentType, writable);
	}
	
	public boolean publishHTTPResponse(HTTPFieldReader<?> reqeustReader, 
			   int statusCode, boolean hasContinuation,
			   HTTPContentType contentType,
			   Writable writable) {
		assert((0 != (msgCommandChannel.initFeatures & MsgCommandChannel.NET_RESPONDER))) : "CommandChannel must be created with NET_RESPONDER flag";
		
		return publishHTTPResponse(reqeustReader.getConnectionId(), reqeustReader.getSequenceCode(),
				                statusCode, hasContinuation, contentType, writable);	
	}
	
	public boolean publishHTTPResponse(long connectionId, long sequenceCode, int statusCode) {
		return publishHTTPResponse(connectionId, sequenceCode, statusCode, false, null, Writable.NO_OP);
	}
	
	public boolean publishHTTPResponse(long connectionId, long sequenceCode, 
            int statusCode, 
            boolean hasContinuation,
            HTTPContentType contentType,
            Writable writable) {
		assert(null!=writable);
		assert((0 != (msgCommandChannel.initFeatures & MsgCommandChannel.NET_RESPONDER))) : "CommandChannel must be created with NET_RESPONDER flag";
		
		final int sequenceNo = 0xFFFFFFFF & (int)sequenceCode;
		final int parallelIndex = 0xFFFFFFFF & (int)(sequenceCode>>32);
		
		assert(1==msgCommandChannel.lastResponseWriterFinished) : "Previous write was not ended can not start another.";
			
		Pipe<ServerResponseSchema> pipe = msgCommandChannel.netResponse.length>1 ? msgCommandChannel.netResponse[parallelIndex] : msgCommandChannel.netResponse[0];
		
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
		msgCommandChannel.holdEmptyBlock(connectionId, sequenceNo, pipe);
		
		
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
			context = HTTPFieldReader.END_OF_RESPONSE;
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
		
		outputStream.publishWithHeader(msgCommandChannel.block1HeaderBlobPosition, msgCommandChannel.block1PositionOfLen); //closeLowLevelField and publish 
		
		return true;
				
	}

	public boolean publishHTTPResponse(HTTPFieldReader<?> reqeustReader, 
	           HeaderWritable headers, Writable writable) {
		return publishHTTPResponse(reqeustReader.getConnectionId(), reqeustReader.getSequenceCode(),
				false, headers, 200, writable
				);
	}
	
	public boolean publishHTTPResponse(long connectionId, long sequenceCode, 
	           boolean hasContinuation, HeaderWritable headers, int statusCode,
	           Writable writable) {
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
		msgCommandChannel.holdEmptyBlock(connectionId, sequenceNo, pipe);
		
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
			context = HTTPFieldReader.END_OF_RESPONSE;
			msgCommandChannel.lastResponseWriterFinished = 1;	
		}	
		
		DataOutputBlobWriter.openField(outputStream);
		writable.write(outputStream); 
		
		if (hasContinuation) {
			// for chunking we must end this block			
			outputStream.write(MsgCommandChannel.RETURN_NEWLINE);
		}
		
		int len = NetResponseWriter.closeLowLevelField(outputStream); //end of writing the payload    	
		
		Pipe.addIntValue(context, outputStream.getPipe());  //real context    	
		Pipe.confirmLowLevelWrite(outputStream.getPipe());
		   	
		////////////////////Write the header
		
		DataOutputBlobWriter.openFieldAtPosition(outputStream, msgCommandChannel.block1HeaderBlobPosition);
		
		//HACK TODO: must formalize response building..
		outputStream.write(HTTPRevisionDefaults.HTTP_1_1.getBytes());
		
		Appendables.appendValue(outputStream.append(" "),statusCode);
		
		if (200==statusCode) {
			outputStream.append(" OK\r\n");
		} else {
			//TODO: should lookup the right name for this status code
			//      add the right text here..
			outputStream.append(" \r\n");
		}		
		if (null!=headers) {
			headers.write(msgCommandChannel.headerWriter.target(outputStream));	
		}
		outputStream.append("Content-Length: "+len+"\r\n");
		outputStream.append("\r\n");
		
		//outputStream.debugAsUTF8();
		
		int propperLength = DataOutputBlobWriter.length(outputStream);
		Pipe.validateVarLength(outputStream.getPipe(), propperLength);
		Pipe.setIntValue(propperLength, outputStream.getPipe(), msgCommandChannel.block1PositionOfLen); //go back and set the right length.
		outputStream.getPipe().closeBlobFieldWrite();
		
		//now publish both header and payload
		Pipe.publishWrites(outputStream.getPipe());
		
		return true;
	}
	
	
	@Deprecated
	public boolean publishHTTPResponse(long connectionId, long sequenceCode, 
	           boolean hasContinuation,
	           CharSequence headers,
	           Writable writable) {
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
		msgCommandChannel.holdEmptyBlock(connectionId, sequenceNo, pipe);
		
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
			context = HTTPFieldReader.END_OF_RESPONSE;
			msgCommandChannel.lastResponseWriterFinished = 1;	
		}	
		
		DataOutputBlobWriter.openField(outputStream);
		writable.write(outputStream); 
		
		if (hasContinuation) {
			// for chunking we must end this block			
			outputStream.write(MsgCommandChannel.RETURN_NEWLINE);
		}
		
		int len = NetResponseWriter.closeLowLevelField(outputStream); //end of writing the payload    	
		
		Pipe.addIntValue(context, outputStream.getPipe());  //real context    	
		Pipe.confirmLowLevelWrite(outputStream.getPipe());
		   	
		////////////////////Write the header
		
		DataOutputBlobWriter.openFieldAtPosition(outputStream, msgCommandChannel.block1HeaderBlobPosition);
		
		//HACK TODO: must formalize response building..
		outputStream.write(HTTPRevisionDefaults.HTTP_1_1.getBytes());
		outputStream.append(" 200 OK\r\n");
		outputStream.append(headers);
		outputStream.append("Content-Length: "+len+"\r\n");
		outputStream.append("\r\n");
		
		//outputStream.debugAsUTF8();
		
		int propperLength = DataOutputBlobWriter.length(outputStream);
		Pipe.validateVarLength(outputStream.getPipe(), propperLength);
		Pipe.setIntValue(propperLength, outputStream.getPipe(), msgCommandChannel.block1PositionOfLen); //go back and set the right length.
		outputStream.getPipe().closeBlobFieldWrite();
		
		//now publish both header and payload
		Pipe.publishWrites(outputStream.getPipe());
		
		return true;
	}
	
	public boolean publishHTTPResponseContinuation(HTTPFieldReader<?> w, 
			boolean hasContinuation, Writable writable) {
		return publishHTTPResponseContinuation(w.getConnectionId(),w.getSequenceCode(), hasContinuation, writable);
	}
	
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
		msgCommandChannel.holdEmptyBlock(connectionId, sequenceNo, pipe);
		
		///////////////////////////////////////
		//message 2 which contains the chunk
		//////////////////////////////////////	
		
		Pipe.addMsgIdx(pipe, ServerResponseSchema.MSG_TOCHANNEL_100);
		Pipe.addLongValue(connectionId, pipe);
		Pipe.addIntValue(sequenceNo, pipe);	
		NetResponseWriter outputStream = (NetResponseWriter)Pipe.outputStream(pipe);
		
		outputStream.openField(hasContinuation? 0: HTTPFieldReader.END_OF_RESPONSE);
		msgCommandChannel.lastResponseWriterFinished = hasContinuation ? 0 : 1;		
		
		writable.write(outputStream); 
		//this is not the end of the data so we must close this block
		outputStream.write(MsgCommandChannel.RETURN_NEWLINE);
		
		
		if (1 == msgCommandChannel.lastResponseWriterFinished) {			
			//this is the end of the data, we must close the block
			//and add the zero trailer
			
			//this adds 3, note the publishWithChunkPrefix also takes this into account
			Appendables.appendHexDigitsRaw(outputStream, 0);
			outputStream.write(AbstractAppendablePayloadResponseStage.RETURN_NEWLINE);
						
			//TODO: add trailing headers here. (no request for this feature yet)
			
			outputStream.write(AbstractAppendablePayloadResponseStage.RETURN_NEWLINE);
		
			
		}
		
		outputStream.publishWithChunkPrefix(msgCommandChannel.block1HeaderBlobPosition, msgCommandChannel.block1PositionOfLen);
		
		return true;
	}
	
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
