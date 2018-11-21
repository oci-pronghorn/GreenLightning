package com.ociweb.gl.impl.file;

import com.ociweb.pronghorn.pipe.ChannelReader;

public interface SequentialPersistanceListenerBase {
    //TODO: use this same API for common shared state across cluster.
	boolean fromStoreReplayBegin();
	boolean fromStoreReplayBlock(long blockId, ChannelReader reader);
	boolean fromStoreReplayEnd();
	boolean fromStoreAckRelease(long blockId);
	boolean fromStoreAckWrite(long blockId);
		
}
