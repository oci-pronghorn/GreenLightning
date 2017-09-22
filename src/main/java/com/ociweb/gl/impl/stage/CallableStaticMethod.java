package com.ociweb.gl.impl.stage;

import com.ociweb.pronghorn.pipe.ChannelReader;

public interface CallableStaticMethod<T> {
	
	boolean method(T that, CharSequence title, ChannelReader reader);
	
}
