package com.ociweb.gl.api;

import com.ociweb.pronghorn.pipe.ChannelReader;

public interface SerialStoreReplayListener {

	boolean replayBegin(int storeId);

	boolean replayFinish(int storeId);

	boolean replay(int storeId,  long value, ChannelReader reader);

}
