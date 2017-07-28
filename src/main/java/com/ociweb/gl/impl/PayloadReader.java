package com.ociweb.gl.impl;

import com.ociweb.pronghorn.pipe.DataInputBlobReader;
import com.ociweb.pronghorn.pipe.DataOutputBlobWriter;
import com.ociweb.pronghorn.pipe.MessageSchema;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.util.TrieParserReader;

public class PayloadReader<S extends MessageSchema<S>> extends DataInputBlobReader<S> {

	private TrieParserReader reader = new TrieParserReader(true);
	private int limit = -1;
	
	public PayloadReader(Pipe<S> pipe) {
        super(pipe);
    }
	

    protected static <S extends MessageSchema<S>> void checkLimit(PayloadReader<S> that, int min) {
    	
    	//TODO: this bounds checker is broken, open does not set limit right since static is used
    	
    	//if ( (that.position+min) > that.limit ) {
    	//	throw new RuntimeException("Read attempted beyond the end of the field data");
    	//}
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

	private int fieldIdx(long fieldId) {
		return (int)fieldId & 0xFFFF;
	}

	protected int fieldType(long fieldId) {
		return (((int)fieldId)>>16) & 0xFF;
	}

	protected int computePosition(long fieldId) {
		assert(fieldId>=0) : "check field name, it does not match any found field";
		//jump to end and index backwards to find data position
		return readFromEndLastInt(fieldIdx(fieldId));	

	}
	
	protected int computePositionSecond(long fieldId) {
		assert(fieldId>=0) : "check field name, it does not match any found field";
		//jump to end and index backwards to find data position
		return readFromEndLastInt(1+fieldIdx(fieldId));		
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
	public <A extends Appendable> A readPackedChars(A target) {
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
