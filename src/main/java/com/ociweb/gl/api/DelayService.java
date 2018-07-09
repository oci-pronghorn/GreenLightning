package com.ociweb.gl.api;

import com.ociweb.pronghorn.pipe.FieldReferenceOffsetManager;
import com.ociweb.pronghorn.pipe.Pipe;

public class DelayService {

	private final MsgCommandChannel<?> msgCommandChannel;

	public DelayService(MsgCommandChannel<?> msgCommandChannel) {
		this.msgCommandChannel = msgCommandChannel;
		msgCommandChannel.initFeatures |= MsgCommandChannel.USE_DELAY;
	}

	/**
	 *
	 * @param messageCount number of messages
	 */
	public boolean hasRoomFor(int messageCount) {
		return null==msgCommandChannel.goPipe || Pipe.hasRoomForWrite(msgCommandChannel.goPipe, 
		FieldReferenceOffsetManager.maxFragmentSize(Pipe.from(msgCommandChannel.goPipe))*messageCount);
	}

	/**
	 *
	 * @param durationNanos delay in nanoseconds
	 * @return true if msgCommandChannel.goHasRoom <p> else return false
	 */
	 public boolean delay(long durationNanos) {
		 assert(msgCommandChannel.enterBlockOk()) : "Concurrent usage error, ensure this never called concurrently";
		try {
		    if (msgCommandChannel.goHasRoom()) {
		    	MsgCommandChannel.publishBlockChannel(durationNanos, msgCommandChannel);
		        return true;
		    } else {
		        return false;
		    }
		} finally {
		    assert(msgCommandChannel.exitBlockOk()) : "Concurrent usage error, ensure this never called concurrently";      
		}
	 }

	/**
	 *
	 * @param msTime time to delay in miliseconds
	 * @return true if msgCommandChannel.goHasRoom <p> else return false
	 */
	public boolean delayUntil(long msTime) {
		 assert(msgCommandChannel.enterBlockOk()) : "Concurrent usage error, ensure this never called concurrently";
		try {
		    if (msgCommandChannel.goHasRoom()) {
		    	MsgCommandChannel.publishBlockChannelUntil(msTime, msgCommandChannel);
		        return true;
		    } else {
		        return false;
		    }
		} finally {
		    assert(msgCommandChannel.exitBlockOk()) : "Concurrent usage error, ensure this never called concurrently";      
		}
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
