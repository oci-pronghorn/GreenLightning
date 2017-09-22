package com.ociweb.gl.api;

import com.ociweb.pronghorn.pipe.ChannelWriter;

public interface NetResponseTemplateData<T> {

	void fetch(ChannelWriter writer, T source);

}
