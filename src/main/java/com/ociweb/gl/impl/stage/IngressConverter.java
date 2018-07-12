package com.ociweb.gl.impl.stage;

import com.ociweb.pronghorn.pipe.ChannelReader;
import com.ociweb.pronghorn.pipe.ChannelWriter;

public interface IngressConverter {

	void convertData(ChannelReader inputStream, ChannelWriter outputStream);
	
}
