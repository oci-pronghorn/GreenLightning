package com.ociweb.gl.impl.pubField;

public interface BytesFieldProcessor {

	public boolean process(byte[] backing, int position, int length, int mask);
	
}
