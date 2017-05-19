package com.ociweb.gl.impl;

import com.ociweb.gl.api.FieldReader;
import com.ociweb.pronghorn.pipe.DataInputBlobReader;
import com.ociweb.pronghorn.pipe.MessageSchema;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.util.Appendables;
import com.ociweb.pronghorn.util.TrieParser;
import com.ociweb.pronghorn.util.TrieParserReader;
import com.ociweb.pronghorn.util.math.Decimal;

public class PayloadReader<S extends MessageSchema<S>> extends DataInputBlobReader<S> implements FieldReader{
	//TODO: Extend class as HTTPPayload reader to hold other fields
	//      extended class provides a header visitor of some kind.

	private TrieParser extractionParser;
	private TrieParserReader reader = new TrieParserReader(true);
	
	public PayloadReader(Pipe<S> pipe) {
        super(pipe);
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
		return reader.query(reader, extractionParser, fieldName, 0, fieldName.length, Integer.MAX_VALUE);
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
		return DataInputBlobReader.readPackedLong(this);
	}
	
	public double getDoubleDirect(long fieldId) {
		assert(TrieParser.ESCAPE_CMD_DECIMAL == fieldType(fieldId));
		position(computePosition(fieldId));
		return Decimal.asDouble(readPackedLong(this), readByte());
	}
	
	public <A extends Appendable> A getTextDirect(long fieldId, A appendable) {
		assert(TrieParser.ESCAPE_CMD_BYTES == fieldType(fieldId));
		position(computePosition(fieldId));		
		readUTF(appendable);
		return appendable;
	}
		
	public long getRationalNumeratorDirect(byte[] fieldName) {
		return getRationalNumeratorDirect(getFieldId(fieldName));		
	}
	
	public long getRationalNumeratorDirect(long fieldId) {
		assert(TrieParser.ESCAPE_CMD_RATIONAL == fieldType(fieldId));
		position(computePosition(fieldId));
		return DataInputBlobReader.readPackedLong(this);
	}
	
	public long getRationalDenominatorDirect(byte[] fieldName) {
		return getRationalDenominatorDirect(getFieldId(fieldName));		
	}
	
	public long getRationalDenominatorDirect(long fieldId) {		
		assert(TrieParser.ESCAPE_CMD_RATIONAL == fieldType(fieldId));
		position(computePositionSecond(fieldId));
		return DataInputBlobReader.readPackedLong(this);
	}
	
	public long getDecimalMantissaDirect(byte[] fieldName) {
		return getDecimalMantissaDirect(getFieldId(fieldName));		
	}
	
	public long getDecimalMantissaDirect(long fieldId) {
		assert(TrieParser.ESCAPE_CMD_DECIMAL == fieldType(fieldId));
		position(computePosition(fieldId));
		return DataInputBlobReader.readPackedLong(this);
	}
	
	public byte getDecimalExponentDirect(byte[] fieldName) {
		return (byte)getDecimalExponentDirect(getFieldId(fieldName));		
	}
	
	public byte getDecimalExponentDirect(long fieldId) {
		assert(TrieParser.ESCAPE_CMD_DECIMAL == fieldType(fieldId));
		position(computePositionSecond(fieldId));
		return readByte();
	}
	
	public double getDouble(byte[] fieldName) {
		return getDouble(getFieldId(fieldName));		
	}
	
	@SuppressWarnings("unchecked")
	public double getDouble(long fieldId) {
		
		position(computePosition(fieldId));
		
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
			return DataInputBlobReader.readPackedLong(this);
		} else if (type == TrieParser.ESCAPE_CMD_DECIMAL) {
			position(computePosition(fieldId));
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
		
		position(computePosition(fieldId));
		
		int type = fieldType(fieldId);
		if (type == TrieParser.ESCAPE_CMD_BYTES) {			
			readUTF(appendable);
			return appendable;
		} else if (type == TrieParser.ESCAPE_CMD_SIGNED_INT) {
			Appendables.appendValue(appendable, readLong());
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
		
		int type = fieldType(fieldId);
		if (type == TrieParser.ESCAPE_CMD_BYTES) {			
			return parseUTF(reader, trie);
		}
		throw new UnsupportedOperationException("unsupported type "+type);
	}
    

}
