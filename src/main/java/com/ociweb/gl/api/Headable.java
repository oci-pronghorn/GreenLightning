package com.ociweb.gl.api;

import com.ociweb.pronghorn.network.config.HTTPHeader;
import com.ociweb.pronghorn.pipe.ChannelReader;

public interface Headable {

	public void read(HTTPHeader header, ChannelReader reader);

}
