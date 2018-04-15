package com.ociweb.gl.api;

import com.ociweb.pronghorn.network.config.HTTPHeader;
import com.ociweb.pronghorn.pipe.ChannelReader;
import com.ociweb.pronghorn.struct.StructFieldVisitor;

public interface Headable extends StructFieldVisitor<HTTPHeader>{

	public void read(HTTPHeader header, ChannelReader reader);

}
