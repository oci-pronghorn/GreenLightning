package com.ociweb.gl.api;

public interface FieldReader {

	public long getFieldId(byte[] fieldName);

	public long getLong(byte[] fieldName);
	public long getLong(long fieldId);
	
	public int getInt(byte[] fieldName);
	public int getInt(int fieldName);
	
	public short getShort(byte[] fieldName);
	public short getShort(int fieldName);
	
	public byte getByte(byte[] fieldName);
	public byte getByte(int fieldName);
	
	public double getDouble(byte[] fieldName);
	public double getDouble(long fieldId);
	
	public long getRationalNumerator(byte[] fieldName);
	public long getRationalNumerator(long fieldId);
	
	public long getRationalDenominator(byte[] fieldName);
	public long getRationalDenominator(long fieldId);
	
	public <A extends Appendable> A getText(long fieldId, A appendable);
		
	public long getLongDirect(long fieldId);	
	public double getDoubleDirect(long fieldId);	
	public <A extends Appendable> A getTextDirect(long fieldId, A appendable);	
	public long getRationalDenominatorDirect(long fieldId);
	public long getRationalNumeratorDirect(long fieldId);
	public long getDecimalMantissaDirect(long fieldId);	
	public byte getDecimalExponentDirect(long fieldId);
	
}
