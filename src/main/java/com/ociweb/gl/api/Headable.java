package com.ociweb.gl.api;

import com.ociweb.pronghorn.pipe.ChannelReader;

public interface Headable {

	public void read(int headerId, ChannelReader reader);

}
