package com.ociweb.gl.api;

public interface HeaderReader {

	//read decimal point position
	public byte readByte();
	
	//read a numeric value
	public long readPackedLong();
	
	//read text in header
	public <A extends Appendable> A readUTF(A target);
	
}
