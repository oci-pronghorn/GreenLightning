package com.ociweb.gl.api;

import com.ociweb.pronghorn.pipe.DataInputBlobReader;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.util.Appendables;
import com.ociweb.pronghorn.util.TrieParser;
import com.ociweb.pronghorn.util.TrieParserReader;
import com.ociweb.pronghorn.util.math.Decimal;

@SuppressWarnings("rawtypes")
public class PayloadReader extends DataInputBlobReader implements FieldReader{
	//TODO: Extend class as HTTPPayload reader to hold other fields
	//      extended class provides a header visitor of some kind.
	
	//TODO: as with PayloadWriter needs a bounds check for the makers
    //TODO: add new interface to hide the reader methods...
	
	//TODO: all stream publish methods must track positons backwards on end.
	//      do for all publishers and for http to avoid headers.
	
	//TODO: this class is awkward in that it will bring in un-used methods
	//TODO: need to track memory usage.
	
	private TrieParser extractionParser;
	private TrieParserReader reader = new TrieParserReader(true);
	
	public PayloadReader(Pipe pipe) {
        super(pipe);
    }

	public void setFieldNameParser(TrieParser extractionParser) {
		extractionParser = extractionParser;
	}
    	
	public long getFieldId(byte[] fieldName) {
		return reader.query(reader, extractionParser, fieldName, 0, fieldName.length, Integer.MAX_VALUE);
	}


	private int fieldIdx(long fieldId) {
		return (int)fieldId & 0xFFFF;
	}

	private int fieldType(long fieldId) {
		return (((int)fieldId)>>16) & 0xFF;
	}

	private int computePosition(long fieldId) {
		//jump to end and index backwards to find data position
		return readFromEndLastInt(fieldIdx(fieldId));		
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
		throw new UnsupportedOperationException("unknown type "+type);
	}
	
	public long getRationalNumerator(byte[] fieldName) {
		return getRationalNumerator(getFieldId(fieldName));		
	}
	
	@SuppressWarnings("unchecked")
	public long getRationalNumerator(long fieldId) {
		
		position(computePosition(fieldId));
		
		int type = fieldType(fieldId);
		if (type == TrieParser.ESCAPE_CMD_RATIONAL) {
			long numerator = DataInputBlobReader.readPackedLong(this);
			DataInputBlobReader.readPackedLong(this);
			return numerator;
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
		
		position(computePosition(fieldId));
		
		int type = fieldType(fieldId);
		if (type == TrieParser.ESCAPE_CMD_RATIONAL) {
			DataInputBlobReader.readPackedLong(this);
			long denominator = DataInputBlobReader.readPackedLong(this);
			return denominator;
		} else if (type == TrieParser.ESCAPE_CMD_DECIMAL) {
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
    

}
