package com.ociweb.gl.api;

public interface PubSubStructuredWriter {

	public void writeInt(int fieldId, int value);
	public void writeLong(int fieldId, long value);
	public void writeBytes(int fieldId, byte[] backing, int offset, int length, int mask);
	public void writeBytes(int fieldId, byte[] backing, int offset, int length);
	public void writeBytes(int fieldId, byte[] backing);	
	public void writeUTF8(int fieldId, CharSequence text, int offset, int length);
	public void writeUTF8(int fieldId, CharSequence text);
	public void writeDecimal(int fieldId, byte e, long m);
	public void writeDouble(int fieldId, double value, byte places);
	public void writeFloat(int fieldId, float value, byte places);		
	public void writeRational(int fieldId, long numerator, long denominator);
}
