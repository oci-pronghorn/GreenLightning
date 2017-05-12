package com.ociweb.gl.api;

public interface NetResponseTemplateData<T> {

	void fetch(NetResponseWriter writer, T source);

}
