package com.ociweb.gl.impl.stage;

import com.ociweb.pronghorn.pipe.ChannelReader;

public interface CallableMethod {
	
	boolean method(CharSequence title, ChannelReader reader);
	
}
