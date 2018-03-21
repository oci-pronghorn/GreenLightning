package com.ociweb.gl.api;

import com.ociweb.gl.impl.http.server.HTTPPayloadReader;
import com.ociweb.pronghorn.network.config.HTTPSpecification;
import com.ociweb.pronghorn.network.config.HTTPVerbDefaults;
import com.ociweb.pronghorn.network.http.FieldExtractionDefinitions;
import com.ociweb.pronghorn.network.http.HTTP1xRouterStageConfig;
import com.ociweb.pronghorn.network.schema.HTTPRequestSchema;
import com.ociweb.pronghorn.pipe.ChannelWriter;
import com.ociweb.pronghorn.pipe.DataInputBlobReader;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.util.hash.IntHashTable;
import com.ociweb.pronghorn.util.Appendables;
import com.ociweb.pronghorn.util.TrieParser;
import com.ociweb.pronghorn.util.TrieParserReader;
import com.ociweb.pronghorn.util.math.Decimal;

public class HTTPRequestReader extends HTTPPayloadReader<HTTPRequestSchema> implements HTTPFieldReader<HTTPRequestSchema> {

	private long connectionId;
	private long sequenceCode;
	private int revisionId;
	private int routeId;
	private int requestContext;
	private HTTPVerbDefaults verb;
	private final boolean hasNoRoutes;
	private HTTP1xRouterStageConfig<?, ?, ?, ?> http1xRouterStageConfig;
	
	
	public HTTPRequestReader(Pipe<HTTPRequestSchema> pipe, boolean hasNoRoutes) {
		super(pipe);
		this.hasNoRoutes = hasNoRoutes;
	}

	
	public void setParseDetails(TrieParser extractionParser, 
			                    IntHashTable table, 
			                    int paraIndexCount, 
			                    TrieParser headerTrieParser,
			                    HTTPSpecification httpSpec,
			                    //TODO: rename this, it should not be HTTP1.x specific but more general...
			                    HTTP1xRouterStageConfig<?, ?, ?, ?> http1xRouterStageConfig) {
		
		this.paraIndexCount = paraIndexCount+1; //count of fields before headers which are before the payload
		this.extractionParser = extractionParser;
		this.headerHash = table;
		this.headerTrieParser = headerTrieParser;
		this.httpSpec = httpSpec;
		this.payloadIndexOffset = paraIndexCount + IntHashTable.count(headerHash) + 1;
		this.http1xRouterStageConfig = http1xRouterStageConfig;
		
	}
	
	public void setVerb(HTTPVerbDefaults verb) {
		this.verb = verb;
	}

	public HTTPVerbDefaults getVerb() {
		return this.verb;
	}
	

	public boolean isVerbGet() {
		return HTTPVerbDefaults.GET == verb;
	}
	
	public boolean isVerbConnect() {
		return HTTPVerbDefaults.CONNECT == verb;
	}
	
	public boolean isVerbDelete() {
		return HTTPVerbDefaults.DELETE == verb;
	}

	public boolean isVerbHead() {
		return HTTPVerbDefaults.HEAD == verb;
	}

	public boolean isVerbOptions() {
		return HTTPVerbDefaults.OPTIONS == verb;
	}

	public boolean isVerbPatch() {
		return HTTPVerbDefaults.PATCH == verb;
	}

	public boolean isVerbPost() {
		return HTTPVerbDefaults.POST == verb;
	}
	
	public boolean isVerbPut() {
		return HTTPVerbDefaults.PUT == verb;
	}
	
	public boolean isVerbTrace() {
		return HTTPVerbDefaults.TRACE == verb;
	}


	public void setRequestContext(int value) {
		requestContext = value;
	}
	
	public int getRequestContext() {
		return requestContext;
	}

	public void setRouteId(int pathId) {
		
		this.fieldDefs = this.http1xRouterStageConfig.extractionParser(pathId);
		this.routeId = fieldDefs.groupId;
		
	}

	public int getRouteId() {
		return routeId;
	}
	
	public void setConnectionId(long connectionId, long sequenceCode) {
		this.connectionId = connectionId;
		this.sequenceCode = sequenceCode;
	}
	
	public void handoff(ChannelWriter writer) {
		writer.writePackedLong(connectionId);
		writer.writePackedLong(sequenceCode);
	}
	
	public long getConnectionId() {
		return connectionId;
	}
	
	public long getSequenceCode() {
		return sequenceCode;
	}


	public void setRevisionId(int value) {
		revisionId = value;
	}
	
	public int getRevisionId() {
		return revisionId;
	}
	
	private FieldExtractionDefinitions fieldDefs;
	protected TrieParser extractionParser;
	
	public long getFieldId(byte[] fieldName) {
		long id = TrieParserReader.query(reader, extractionParser, fieldName, 0, fieldName.length, Integer.MAX_VALUE);
		if (id<0) {
			throw new UnsupportedOperationException("unknown field name '"+new String(fieldName)+"'");
		}
		
//		Appendables.appendHexDigits(
//		Appendables.appendUTF8(System.out, fieldName,0,fieldName.length,Integer.MAX_VALUE)
//		           .append(" -> ID: "),id).append('\n');

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
	
	public long getRationalDenominatorDirect(byte[] fieldName) {
		return getRationalDenominatorDirect(getFieldId(fieldName));		
	}
	
	public long getDecimalMantissaDirect(byte[] fieldName) {
		return getDecimalMantissaDirect(getFieldId(fieldName));		
	}
	
	public byte getDecimalExponentDirect(byte[] fieldName) {
		return (byte)getDecimalExponentDirect(getFieldId(fieldName));		
	}
	
	public double getDouble(byte[] fieldName) {
		return getDouble(getFieldId(fieldName));		
	}
	
	public long getRationalNumerator(byte[] fieldName) {
		return getRationalNumerator(getFieldId(fieldName));		
	}
	public long getRationalDenominator(byte[] fieldName) {
		return getRationalDenominator(getFieldId(fieldName));		
	}
	
	public <A extends Appendable> A getText(byte[] fieldName, A appendable) {
		return getText(getFieldId(fieldName),appendable);		
	}
	
	@Override
	public boolean isEqual(byte[] fieldName, byte[] equalText) {
		return isEqual(getFieldId(fieldName),equalText);
	}
	
	@Override
	public long trieText(byte[] fieldName, TrieParserReader reader, TrieParser trie) {
		return trieText(getFieldId(fieldName),reader,trie);
	}
	
	public long getRationalNumeratorDirect(byte[] fieldName) {
		return getRationalNumeratorDirect(getFieldId(fieldName));		
	}
	
	////////////////
	///////////////
	///////////////
	
	
	@SuppressWarnings("unchecked")
	public long getLong(long fieldId) {
		
		if (0!=(fieldId & FieldExtractionDefinitions.DEFAULT_VALUE_FLAG) ) {			
			return fieldDefs.getDefaultInteger((int)fieldId);
		}
		
		position(this, computePosition(fieldId));
		
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
		
		if (0!=(fieldId & FieldExtractionDefinitions.DEFAULT_VALUE_FLAG) ) {			
			return fieldDefs.getDefaultInteger((int)fieldId);
		}
		
		assert(TrieParser.ESCAPE_CMD_SIGNED_INT == fieldType(fieldId));
		position(this, computePosition(fieldId));
		checkLimit(this,1);
		return DataInputBlobReader.readPackedLong(this);
	}
	
	public double getDoubleDirect(long fieldId) {
		
		if (0!=(fieldId & FieldExtractionDefinitions.DEFAULT_VALUE_FLAG) ) {			
			return fieldDefs.getDefaultDouble((int)fieldId);
		}
		
		assert(TrieParser.ESCAPE_CMD_DECIMAL == fieldType(fieldId));
		position(this, computePosition(fieldId));
		checkLimit(this,2);
		return Decimal.asDouble(readPackedLong(this), readByte());
	}
	
	public <A extends Appendable> A getTextDirect(long fieldId, A appendable) {
		
		if (0!=(fieldId & FieldExtractionDefinitions.DEFAULT_VALUE_FLAG) ) {			
			fieldDefs.appendDefaultText((int)fieldId, appendable);			
		}
		
		assert(TrieParser.ESCAPE_CMD_BYTES == fieldType(fieldId));
		position(this, computePosition(fieldId));	
		checkLimit(this,2);
		readUTF(appendable);
		return appendable;
	}

	
	public long getRationalNumeratorDirect(long fieldId) {
		if (0!=(fieldId & FieldExtractionDefinitions.DEFAULT_VALUE_FLAG) ) {			
			return fieldDefs.getDefaultNumerator((int)fieldId);
		}
		assert(TrieParser.ESCAPE_CMD_RATIONAL == fieldType(fieldId));
		position(this, computePosition(fieldId));
		checkLimit(this,1);
		return DataInputBlobReader.readPackedLong(this);
	}
		
	public long getRationalDenominatorDirect(long fieldId) {
		if (0!=(fieldId & FieldExtractionDefinitions.DEFAULT_VALUE_FLAG) ) {			
			return fieldDefs.getDefaultDenominator((int)fieldId);
		}
		assert(TrieParser.ESCAPE_CMD_RATIONAL == fieldType(fieldId));
		position(this, computePositionSecond(fieldId));
		checkLimit(this,1);
		return DataInputBlobReader.readPackedLong(this);
	}
	
	
	public long getDecimalMantissaDirect(long fieldId) {
		
		if (0!=(fieldId & FieldExtractionDefinitions.DEFAULT_VALUE_FLAG) ) {			
			return fieldDefs.getDefaultDecimalM((int)fieldId);
		}
		
		assert(TrieParser.ESCAPE_CMD_DECIMAL == fieldType(fieldId));
		position(this, computePosition(fieldId));
		checkLimit(this,1);
		return DataInputBlobReader.readPackedLong(this);
	}
	

	
	public byte getDecimalExponentDirect(long fieldId) {
		
		if (0!=(fieldId & FieldExtractionDefinitions.DEFAULT_VALUE_FLAG) ) {			
			return fieldDefs.getDefaultDecimalE((int)fieldId);
		}
		
		assert(TrieParser.ESCAPE_CMD_DECIMAL == fieldType(fieldId));
		position(this, computePositionSecond(fieldId));
		checkLimit(this,1);
		return readByte();
	}

	
	@SuppressWarnings("unchecked")
	public double getDouble(long fieldId) {
		
		if (0!=(fieldId & FieldExtractionDefinitions.DEFAULT_VALUE_FLAG) ) {			
			return fieldDefs.getDefaultDouble((int)fieldId);
		}
		
		position(this, computePosition(fieldId));
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
	

		
	@SuppressWarnings("unchecked")
	public long getRationalNumerator(long fieldId) {
		
		if (0!=(fieldId & FieldExtractionDefinitions.DEFAULT_VALUE_FLAG) ) {			
			return fieldDefs.getDefaultNumerator((int)fieldId);
		}
		
		position(this, computePosition(fieldId));
		checkLimit(this,1);
		
		int type = fieldType(fieldId);
		if (type == TrieParser.ESCAPE_CMD_RATIONAL) {
			return DataInputBlobReader.readPackedLong(this);
		} else if (type == TrieParser.ESCAPE_CMD_DECIMAL) {
			long m = readPackedLong(); 
			byte e = readByte();
			return Decimal.asNumerator(m, e);
		} else if (type == TrieParser.ESCAPE_CMD_SIGNED_INT) {
			return DataInputBlobReader.readPackedLong(this);			
		} else if (type == TrieParser.ESCAPE_CMD_BYTES) {
			return DataInputBlobReader.readUTFAsLong(this);
		} 
		throw new UnsupportedOperationException("unknown type "+type);
	}




	@SuppressWarnings("unchecked")
	public long getRationalDenominator(long fieldId) {
				
		if (0!=(fieldId & FieldExtractionDefinitions.DEFAULT_VALUE_FLAG) ) {			
			return fieldDefs.getDefaultDenominator((int)fieldId);
		}
		
		int type = fieldType(fieldId);
		if (type == TrieParser.ESCAPE_CMD_RATIONAL) {
			position(this, computePositionSecond(fieldId));
			checkLimit(this,1);
			return DataInputBlobReader.readPackedLong(this);
		} else if (type == TrieParser.ESCAPE_CMD_DECIMAL) {
			position(this, computePosition(fieldId));
			checkLimit(this,1);
			DataInputBlobReader.readPackedLong(this); 
			byte e = readByte();
			return Decimal.asDenominator(e);
		} else if (type == TrieParser.ESCAPE_CMD_SIGNED_INT) {
			return 1;			
		} else if (type == TrieParser.ESCAPE_CMD_BYTES) {
			return 1;
		} 
		throw new UnsupportedOperationException("unknown type "+type);
	}



	
	/**
	 * Only call this method when NO routes have been defined.
	 * @param appendable
	 * 
	 */
	public <A extends Appendable> A getRoutePath(A appendable) {
		if (hasNoRoutes) {		
			int assumedId = 0x620001;
			assert(getFieldId("path".getBytes()) == assumedId) : "error: "+getFieldId("path".getBytes());	
			return getText(assumedId,appendable);		
		} else {
			throw new UnsupportedOperationException("this method can only be used when no routes have been defined.");
		}
	}
	
	
	@SuppressWarnings("unchecked")
	public <A extends Appendable> A getText(long fieldId, A appendable) {
		
		if (fieldId<0) {
			throw new UnsupportedOperationException("unknown field name");
		}

		if (0!=(fieldId & FieldExtractionDefinitions.DEFAULT_VALUE_FLAG) ) {			
			return fieldDefs.appendDefaultText((int)fieldId, appendable);
		}
				
		position(this, computePosition(fieldId));
				
		int type = fieldType(fieldId);
		if (type == TrieParser.ESCAPE_CMD_BYTES) {
			checkLimit(this,2);
			readUTF(appendable);
			return appendable;
		} else if (type == TrieParser.ESCAPE_CMD_SIGNED_INT) {
			checkLimit(this,1);
			Appendables.appendValue(appendable, readPackedLong());
			return appendable;			
		} else if (type == TrieParser.ESCAPE_CMD_RATIONAL) {
			checkLimit(this,2);
			long numerator = DataInputBlobReader.readPackedLong(this);
			long denominator = DataInputBlobReader.readPackedLong(this);
			Appendables.appendValue(Appendables.appendValue(appendable, numerator),"/",denominator);	
			return appendable;
		} else if (type == TrieParser.ESCAPE_CMD_DECIMAL) {
			checkLimit(this,2);
			long m = DataInputBlobReader.readPackedLong(this); 
			byte e = readByte();
			Appendables.appendDecimalValue(appendable, m, e);
			return appendable;
		}
		throw new UnsupportedOperationException("unknown field type "+type);
	}



	@Override
	public boolean isEqual(long fieldId, byte[] equalText) {
		
		if (0!=(fieldId & FieldExtractionDefinitions.DEFAULT_VALUE_FLAG) ) {			
			return fieldDefs.isEqualDefaultText((int)fieldId, equalText);
		}
		
		position(this, computePosition(fieldId));
		checkLimit(this,2);
		
		int type = fieldType(fieldId);
		if (type == TrieParser.ESCAPE_CMD_BYTES) {
			return equalUTF(equalText);
		}
		throw new UnsupportedOperationException("unsupported type "+type);
	}





	@Override
	public long trieText(long fieldId, TrieParserReader reader, TrieParser trie) {

		if (0!=(fieldId & FieldExtractionDefinitions.DEFAULT_VALUE_FLAG) ) {			
			return fieldDefs.parse((int)fieldId, reader, trie);
		}
		
		position(this, computePosition(fieldId));
		checkLimit(this,2);
		
		int type = fieldType(fieldId);
		if (type == TrieParser.ESCAPE_CMD_BYTES) {			
			int length = readShort();
			return parse(reader, trie, length);
		}
		throw new UnsupportedOperationException("unsupported type "+type);
	}

}
