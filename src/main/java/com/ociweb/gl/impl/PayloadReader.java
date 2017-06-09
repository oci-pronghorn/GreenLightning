package com.ociweb.gl.impl;

import java.io.IOException;

import com.ociweb.gl.api.FieldReader;
import com.ociweb.pronghorn.pipe.DataInputBlobReader;
import com.ociweb.pronghorn.pipe.DataOutputBlobWriter;
import com.ociweb.pronghorn.pipe.MessageSchema;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.util.Appendables;
import com.ociweb.pronghorn.util.TrieParser;
import com.ociweb.pronghorn.util.TrieParserReader;
import com.ociweb.pronghorn.util.math.Decimal;

public class PayloadReader<S extends MessageSchema<S>> extends DataInputBlobReader<S> implements FieldReader{

	private TrieParser extractionParser;
	private TrieParserReader reader = new TrieParserReader(true);
	private int limit = -1;
	
	public PayloadReader(Pipe<S> pipe) {
        super(pipe);
    }
	

    private static <S extends MessageSchema<S>> void checkLimit(PayloadReader<S> that, int min) {
    	if ( (that.position+min) > that.limit ) {
    		throw new RuntimeException("Read attempted beyond the end of the field data");
    	}
    }
   
	
	@Override
	public int openHighLevelAPIField(int loc) {		
		int len = super.openHighLevelAPIField(loc);
		limit = len + position;
		return len;
	}
	
	@Override
	public int openLowLevelAPIField() {		
		int len = super.openLowLevelAPIField();
		limit = len + position;
		return len;
	}
	
	public void setFieldNameParser(TrieParser extractionParser) {
		this.extractionParser = extractionParser;
	}

	private int fieldIdx(long fieldId) {
		return (int)fieldId & 0xFFFF;
	}

	private int fieldType(long fieldId) {
		return (((int)fieldId)>>16) & 0xFF;
	}

	private int computePosition(long fieldId) {
		assert(fieldId>=0) : "check field name, it does not match any found field";
		//jump to end and index backwards to find data position
		return readFromEndLastInt(fieldIdx(fieldId));		
	}
	
	private int computePositionSecond(long fieldId) {
		assert(fieldId>=0) : "check field name, it does not match any found field";
		//jump to end and index backwards to find data position
		return readFromEndLastInt(1+fieldIdx(fieldId));		
	}

	public long getFieldId(byte[] fieldName) {
		long id = reader.query(reader, extractionParser, fieldName, 0, fieldName.length, Integer.MAX_VALUE);
		if (id<0) {
			throw new UnsupportedOperationException("unknown field name '"+new String(fieldName)+"'");
		}
		return id;
	}
	
	public long getLong(byte[] fieldName) {
		return getLong(getFieldId(fieldName));		
	}

	public int getInt(byte[] fieldName) {
		return (int)getLong(getFieldId(fieldName));		
	}
	
	public int getInt(int fieldName) {
		return (int)getLong(fieldName);		
	}
	
	public short getShort(byte[] fieldName) {
		return (short)getLong(getFieldId(fieldName));		
	}
	
	public short getShort(int fieldName) {
		return (short)getLong(fieldName);		
	}
	
	public byte getByte(byte[] fieldName) {
		return (byte)getLong(getFieldId(fieldName));		
	}
	
	public byte getByte(int fieldName) {
		return (byte)getLong(fieldName);		
	}
	
	@SuppressWarnings("unchecked")
	public long getLong(long fieldId) {
		
		position(computePosition(fieldId));
		
		checkLimit(this,1);
		
		int type = fieldType(fieldId);
		if (type == TrieParser.ESCAPE_CMD_SIGNED_INT) {
			return DataInputBlobReader.readPackedLong(this);			
		} else if (type == TrieParser.ESCAPE_CMD_BYTES) {
			return DataInputBlobReader.readUTFAsLong(this);
		} else if (type == TrieParser.ESCAPE_CMD_RATIONAL) {
			long numerator = DataInputBlobReader.readPackedLong(this);
			long denominator = DataInputBlobReader.readPackedLong(this);
			return numerator/denominator;
		} else if (type == TrieParser.ESCAPE_CMD_DECIMAL) {
			return readDecimalAsLong();
		}
		throw new UnsupportedOperationException("unknown type "+type);
	}
	
	public long getLongDirect(long fieldId) {
		assert(TrieParser.ESCAPE_CMD_SIGNED_INT == fieldType(fieldId));
		position(computePosition(fieldId));
		checkLimit(this,1);
		return DataInputBlobReader.readPackedLong(this);
	}
	
	public double getDoubleDirect(long fieldId) {
		assert(TrieParser.ESCAPE_CMD_DECIMAL == fieldType(fieldId));
		position(computePosition(fieldId));
		checkLimit(this,2);
		return Decimal.asDouble(readPackedLong(this), readByte());
	}
	
	public <A extends Appendable> A getTextDirect(long fieldId, A appendable) {
		assert(TrieParser.ESCAPE_CMD_BYTES == fieldType(fieldId));
		position(computePosition(fieldId));	
		checkLimit(this,2);
		readUTF(appendable);
		return appendable;
	}
		
	public long getRationalNumeratorDirect(byte[] fieldName) {
		return getRationalNumeratorDirect(getFieldId(fieldName));		
	}
	
	public long getRationalNumeratorDirect(long fieldId) {
		assert(TrieParser.ESCAPE_CMD_RATIONAL == fieldType(fieldId));
		position(computePosition(fieldId));
		checkLimit(this,1);
		return DataInputBlobReader.readPackedLong(this);
	}
	
	public long getRationalDenominatorDirect(byte[] fieldName) {
		return getRationalDenominatorDirect(getFieldId(fieldName));		
	}
	
	public long getRationalDenominatorDirect(long fieldId) {		
		assert(TrieParser.ESCAPE_CMD_RATIONAL == fieldType(fieldId));
		position(computePositionSecond(fieldId));
		checkLimit(this,1);
		return DataInputBlobReader.readPackedLong(this);
	}
	
	public long getDecimalMantissaDirect(byte[] fieldName) {
		return getDecimalMantissaDirect(getFieldId(fieldName));		
	}
	
	public long getDecimalMantissaDirect(long fieldId) {
		assert(TrieParser.ESCAPE_CMD_DECIMAL == fieldType(fieldId));
		position(computePosition(fieldId));
		checkLimit(this,1);
		return DataInputBlobReader.readPackedLong(this);
	}
	
	public byte getDecimalExponentDirect(byte[] fieldName) {
		return (byte)getDecimalExponentDirect(getFieldId(fieldName));		
	}
	
	public byte getDecimalExponentDirect(long fieldId) {
		assert(TrieParser.ESCAPE_CMD_DECIMAL == fieldType(fieldId));
		position(computePositionSecond(fieldId));
		checkLimit(this,1);
		return readByte();
	}
	
	public double getDouble(byte[] fieldName) {
		return getDouble(getFieldId(fieldName));		
	}
	
	@SuppressWarnings("unchecked")
	public double getDouble(long fieldId) {
		
		position(computePosition(fieldId));
		checkLimit(this,1);
		
		int type = fieldType(fieldId);
		if (type == TrieParser.ESCAPE_CMD_DECIMAL) {
			return readDecimalAsDouble();
		} else if (type == TrieParser.ESCAPE_CMD_SIGNED_INT) {
			return (double)DataInputBlobReader.readPackedLong(this);			
		} else if (type == TrieParser.ESCAPE_CMD_BYTES) {
			return DataInputBlobReader.readUTFAsDecimal(this);
		} else if (type == TrieParser.ESCAPE_CMD_RATIONAL) {
			double numerator = DataInputBlobReader.readPackedLong(this);
			double denominator = DataInputBlobReader.readPackedLong(this);
			return numerator/denominator;
		} 
		throw new UnsupportedOperationException("unknown type "+type+" field "+Long.toHexString(fieldId));
	}
	
	public long getRationalNumerator(byte[] fieldName) {
		return getRationalNumerator(getFieldId(fieldName));		
	}
		
	@SuppressWarnings("unchecked")
	public long getRationalNumerator(long fieldId) {
		
		position(computePosition(fieldId));
		checkLimit(this,1);
		
		int type = fieldType(fieldId);
		if (type == TrieParser.ESCAPE_CMD_RATIONAL) {
			return DataInputBlobReader.readPackedLong(this);
		} else if (type == TrieParser.ESCAPE_CMD_DECIMAL) {
			long m = readPackedLong(); 
			byte e = readByte();
			return e<0 ? m : Decimal.asLong(m, e);
		} else if (type == TrieParser.ESCAPE_CMD_SIGNED_INT) {
			return DataInputBlobReader.readPackedLong(this);			
		} else if (type == TrieParser.ESCAPE_CMD_BYTES) {
			return DataInputBlobReader.readUTFAsLong(this);
		} 
		throw new UnsupportedOperationException("unknown type "+type);
	}
	
	public long getRationalDenominator(byte[] fieldName) {
		return getRationalDenominator(getFieldId(fieldName));		
	}

	@SuppressWarnings("unchecked")
	public long getRationalDenominator(long fieldId) {
				
		int type = fieldType(fieldId);
		if (type == TrieParser.ESCAPE_CMD_RATIONAL) {
			position(computePositionSecond(fieldId));
			checkLimit(this,1);
			return DataInputBlobReader.readPackedLong(this);
		} else if (type == TrieParser.ESCAPE_CMD_DECIMAL) {
			position(computePosition(fieldId));
			checkLimit(this,1);
			DataInputBlobReader.readPackedLong(this); 
			byte e = readByte();
			return e<0 ? (long)(1d/Decimal.powdi[64 - e]) : 1;
		} else if (type == TrieParser.ESCAPE_CMD_SIGNED_INT) {
			return 1;			
		} else if (type == TrieParser.ESCAPE_CMD_BYTES) {
			return 1;
		} 
		throw new UnsupportedOperationException("unknown type "+type);
	}
	
	public <A extends Appendable> A getText(byte[] fieldName, A appendable) {
		return getText(getFieldId(fieldName),appendable);		
	}
	
	@SuppressWarnings("unchecked")
	public <A extends Appendable> A getText(long fieldId, A appendable) {
		
		if (fieldId<0) {
			throw new UnsupportedOperationException("unknown field name");
		}
		position(computePosition(fieldId));
		checkLimit(this,2);
		
		int type = fieldType(fieldId);
		if (type == TrieParser.ESCAPE_CMD_BYTES) {
			readUTF(appendable);
			return appendable;
		} else if (type == TrieParser.ESCAPE_CMD_SIGNED_INT) {
			Appendables.appendValue(appendable, readPackedLong());
			return appendable;			
		} else if (type == TrieParser.ESCAPE_CMD_RATIONAL) {
			long numerator = DataInputBlobReader.readPackedLong(this);
			long denominator = DataInputBlobReader.readPackedLong(this);
			Appendables.appendValue(Appendables.appendValue(appendable, numerator),"/",denominator);	
			return appendable;
		} else if (type == TrieParser.ESCAPE_CMD_DECIMAL) {
			long m = DataInputBlobReader.readPackedLong(this); 
			byte e = readByte();
			Appendables.appendDecimalValue(appendable, m, e);
			return appendable;
		}
		throw new UnsupportedOperationException("unknown type "+type);
	}

	@Override
	public boolean isEqual(byte[] fieldName, byte[] equalText) {
		return isEqual(getFieldId(fieldName),equalText);
	}

	@Override
	public boolean isEqual(long fieldId, byte[] equalText) {
		
		position(computePosition(fieldId));
		checkLimit(this,2);
		
		int type = fieldType(fieldId);
		if (type == TrieParser.ESCAPE_CMD_BYTES) {
			return equalUTF(equalText);
		}
		throw new UnsupportedOperationException("unsupported type "+type);
	}



	@Override
	public long trieText(byte[] fieldName, TrieParserReader reader, TrieParser trie) {
		return trieText(getFieldId(fieldName),reader,trie);
	}

	@Override
	public long trieText(long fieldId, TrieParserReader reader, TrieParser trie) {

		position(computePosition(fieldId));
		checkLimit(this,2);
		
		int type = fieldType(fieldId);
		if (type == TrieParser.ESCAPE_CMD_BYTES) {			
			return parseUTF(reader, trie);
		}
		throw new UnsupportedOperationException("unsupported type "+type);
	}

	/////////////////////

	@Override
	public int read(byte[] b) {
		checkLimit(this,2);
		return super.read(b);
	}


	@Override
	public int read(byte[] b, int off, int len) {
		checkLimit(this,2);//not len because read will read less
		return super.read(b, off, len);
	}


	@Override
	public void readFully(byte[] b) {
		checkLimit(this,2);
		super.readFully(b);
	}


	@Override
	public void readFully(byte[] b, int off, int len) {
		checkLimit(this,2);//not len because read will read less
		super.readFully(b, off, len);
	}


	@Override
	public int skipBytes(int n) {
		checkLimit(this,n);
		return super.skipBytes(n);
	}


	@Override
	public boolean readBoolean() {
		checkLimit(this,1);
		return super.readBoolean();
	}


	@Override
	public byte readByte() {
		checkLimit(this,1);
		return super.readByte();
	}


	@Override
	public int readUnsignedByte() {
		checkLimit(this,1);
		return super.readUnsignedByte();
	}


	@Override
	public short readShort() {
		checkLimit(this,2);
		return super.readShort();
	}


	@Override
	public int readUnsignedShort() {
		checkLimit(this,2);
		return super.readUnsignedShort();
	}


	@Override
	public char readChar() {
		checkLimit(this,1);
		return super.readChar();
	}


	@Override
	public int readInt() {
		checkLimit(this,4);
		return super.readInt();
	}


	@Override
	public long readLong() {
		checkLimit(this,8);
		return super.readLong();
	}


	@Override
	public float readFloat() {
		checkLimit(this,4);
		return super.readFloat();
	}


	@Override
	public double readDouble() {
		checkLimit(this,8);
		return super.readDouble();
	}


	@Override
	public int read() {
		checkLimit(this,1);
		return super.read();
	}


	@Override
	public String readLine() {
		checkLimit(this,1);
		return super.readLine();
	}


	@Override
	public String readUTF() {
		checkLimit(this,2);
		return super.readUTF();
	}


	@Override
	public <A extends Appendable> A readUTF(A target) {
		checkLimit(this,2);
		return super.readUTF(target);
	}


	@Override
	public Object readObject() {
		checkLimit(this,1);
		return super.readObject();
	}


	@Override
	public <T extends MessageSchema<T>> void readInto(DataOutputBlobWriter<T> writer, int length) {
		checkLimit(this,length);
		super.readInto(writer, length);
	}


	@Override
	public <A extends Appendable> A readPackedChars(A target) throws IOException {
		checkLimit(this,1);
		return super.readPackedChars(target);
	}


	@Override
	public long readPackedLong() {
		checkLimit(this,1);
		return super.readPackedLong();
	}


	@Override
	public int readPackedInt() {
		checkLimit(this,1);
		return super.readPackedInt();
	}


	@Override
	public double readDecimalAsDouble() {
		checkLimit(this,2);
		return super.readDecimalAsDouble();
	}


	@Override
	public long readDecimalAsLong() {
		checkLimit(this,2);
		return super.readDecimalAsLong();
	}


	@Override
	public short readPackedShort() {
		checkLimit(this,1);
		return super.readPackedShort();
	}

	

}
