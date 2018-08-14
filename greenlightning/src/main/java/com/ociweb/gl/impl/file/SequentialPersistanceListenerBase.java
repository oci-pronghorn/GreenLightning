package com.ociweb.gl.impl.file;

import com.ociweb.pronghorn.pipe.ChannelReader;

public interface SequentialPersistanceListenerBase {

	boolean fromStoreReplayBegin();
	boolean fromStoreReplayBlock(long blockId, ChannelReader reader);
	boolean fromStoreReplayEnd();
	boolean fromStoreAckRelease(long blockId);
	boolean fromStoreAckWrite(long blockId);
		
}
