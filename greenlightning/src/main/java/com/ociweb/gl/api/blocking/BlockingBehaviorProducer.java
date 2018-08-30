package com.ociweb.gl.api.blocking;

import com.ociweb.pronghorn.pipe.ChannelReader;

public interface BlockingBehaviorProducer {
	
	boolean unChosenMessages(ChannelReader reader);
	
	BlockingBehavior produce();
}
