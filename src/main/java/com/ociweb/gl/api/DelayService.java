package com.ociweb.gl.api;

public class DelayService {

	private final MsgCommandChannel<?> msgCommandChannel;
	
	public DelayService(MsgCommandChannel<?> msgCommandChannel) {
		this.msgCommandChannel = msgCommandChannel;
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
