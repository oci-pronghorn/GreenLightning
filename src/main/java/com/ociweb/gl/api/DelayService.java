package com.ociweb.gl.api;

import com.ociweb.pronghorn.pipe.FieldReferenceOffsetManager;
import com.ociweb.pronghorn.pipe.Pipe;

public class DelayService {

	private final MsgCommandChannel<?> msgCommandChannel;
	
	public DelayService(MsgCommandChannel<?> msgCommandChannel) {
		this.msgCommandChannel = msgCommandChannel;
		msgCommandChannel.initFeatures |= MsgCommandChannel.USE_DELAY;
	}

	public boolean hasRoomFor(int messageCount) {
		return null==msgCommandChannel.goPipe || Pipe.hasRoomForWrite(msgCommandChannel.goPipe, 
		FieldReferenceOffsetManager.maxFragmentSize(Pipe.from(msgCommandChannel.goPipe))*messageCount);
	}
	
	
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
}
