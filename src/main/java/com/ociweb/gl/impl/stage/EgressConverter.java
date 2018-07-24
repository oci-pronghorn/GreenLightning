package com.ociweb.gl.impl.stage;

import com.ociweb.pronghorn.pipe.ChannelReader;
import com.ociweb.pronghorn.pipe.ChannelWriter;

public interface EgressConverter {

	void convert(ChannelReader inputStream,
			     ChannelWriter outputStream);

}
