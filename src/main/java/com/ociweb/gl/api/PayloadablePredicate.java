package com.ociweb.gl.api;

import com.ociweb.pronghorn.pipe.ChannelReader;

public interface PayloadablePredicate {

	boolean read(ChannelReader reader);

}
