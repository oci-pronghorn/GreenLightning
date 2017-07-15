package com.ociweb.gl.api;

import com.ociweb.pronghorn.pipe.BlobWriter;

public interface NetResponseTemplateData<T> {

	void fetch(BlobWriter writer, T source);

}
