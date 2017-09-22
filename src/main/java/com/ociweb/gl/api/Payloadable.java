package com.ociweb.gl.api;

import com.ociweb.pronghorn.pipe.ChannelReader;

public interface Payloadable {

	void read(ChannelReader reader);

}
