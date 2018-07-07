package com.ociweb.gl.impl.stage;

import com.ociweb.pronghorn.pipe.ChannelReader;
import com.ociweb.pronghorn.pipe.DataOutputBlobWriter;

public interface EgressConverter {

	void convert(ChannelReader inputStream,
			     DataOutputBlobWriter<?> outputStream);

}
