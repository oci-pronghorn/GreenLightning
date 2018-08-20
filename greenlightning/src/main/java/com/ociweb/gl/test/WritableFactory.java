package com.ociweb.gl.test;

import com.ociweb.pronghorn.pipe.ChannelWriter;

public interface WritableFactory {

	void payloadWriter(long callInstance, ChannelWriter writer);
	
}
