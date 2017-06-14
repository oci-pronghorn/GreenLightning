package com.ociweb.gl.impl.pubField;

public interface FieldConsumer {

	public void store(long value);
	public void store(byte[] backing, int pos, int len, int mask);
	public void store(byte e, long m);
	public void store(long numerator, long denominator);
	
}
