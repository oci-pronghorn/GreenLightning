package com.ociweb.gl.api;

import java.io.IOException;

import com.ociweb.pronghorn.network.ServerCoordinator;
import com.ociweb.pronghorn.network.schema.ServerResponseSchema;
import com.ociweb.pronghorn.pipe.DataInputBlobReader;
import com.ociweb.pronghorn.pipe.DataOutputBlobWriter;
import com.ociweb.pronghorn.pipe.Pipe;

public class NetResponseWriter extends DataOutputBlobWriter<ServerResponseSchema> {

    private final Pipe<ServerResponseSchema> p;
    private final int maxLength;
    private int length;
    private long key;
    private int context;
    
    public NetResponseWriter(Pipe<ServerResponseSchema> p) {
    	
    	super(p);
    	this.p = p;    
    	this.maxLength = p.maxVarLen;
        
    }
        
    public void writeString(CharSequence value) {
        writeUTF(value);
    }
    
    public void writeObject(Object object) {
    	
    	try {
			super.writeObject(object);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
    }
    
    public void close() {
    	try {
    		super.close();
    	} catch (IOException e) {
    		throw new RuntimeException();
    	}

    	publish();
    	
    }

    
    public void publish() {
    	
    	closeLowLevelField();
    	
    	Pipe.addIntValue(context, p); 
    	
    	Pipe.confirmLowLevelWrite(p, Pipe.sizeOf(p, ServerResponseSchema.MSG_TOCHANNEL_100)); ///NOTE: modify later wehn we add subscription publish feature
    	Pipe.publishWrites(p);    	
 
    }

    void openField(int context) {
    	this.context = context;
        this.length = 0;
        DataOutputBlobWriter.openField(this);
    }

    private static void checkLimit(NetResponseWriter that, int x) {
    	//TODO: the math on this bounds check is wrong and must be fixed.. It is accumulaing all byte written not just the open ones now.
    //	if ( (that.length+=x) > that.maxLength ) {
   // 		throw new RuntimeException("This field is limited to a maximum length of "+that.maxLength+". Write less data or declare a larger max payload size. Already wrote "+that.length+" attempting to add "+x);
   // 	}
    	
    }
    
    
	@Override
	public void write(int b) {
		checkLimit(this,1);
		super.write(b);
	}

	@Override
	public void write(byte[] b) {
		checkLimit(this,b.length);
		super.write(b);
	}

	@Override
	public void write(byte[] b, int off, int len) {
		checkLimit(this,len);
		super.write(b, off, len);
	}
	
	@Override
	public void writeStream(DataInputBlobReader input, int length) {
		checkLimit(this, length);
		super.writeStream(input, length);
	}

	@Override
	public void writeBoolean(boolean v) {
		checkLimit(this,1);
		super.writeBoolean(v);
	}

	@Override
	public void writeByte(int v) {
		checkLimit(this,1);
		super.writeByte(v);
	}

	@Override
	public void writeShort(int v) {
		checkLimit(this,2);
		super.writeShort(v);
	}

	@Override
	public void writeChar(int v) {
		checkLimit(this,2);
		super.writeChar(v);
	}

	@Override
	public void writeInt(int v) {
		checkLimit(this,4);
		super.writeInt(v);
	}

	@Override
	public void writeLong(long v) {
		checkLimit(this,8);
		super.writeLong(v);
	}

	@Override
	public void writeFloat(float v) {
		checkLimit(this,4);
		super.writeFloat(v);
	}

	@Override
	public void writeDouble(double v) {
		checkLimit(this,8);
		super.writeDouble(v);
	}

	@Override
	public void writeBytes(String s) {
		checkLimit(this,s.length());
		super.writeBytes(s);
	}

	@Override
	public void writeChars(String s) {
		checkLimit(this,s.length()*2);
		super.writeChars(s);
	}

	@Override
	public void writeUTF(String s) {
		checkLimit(this,s.length()*6);//Estimated (maximum length)
		super.writeUTF(s);
	}

	@Override
	public void writeUTF(CharSequence s) {
		checkLimit(this,s.length()*6);//Estimated (maximum length)
		super.writeUTF(s);
	}

	@Override
	public void writeASCII(CharSequence s) {
		checkLimit(this,s.length());
		super.writeASCII(s);
	}

	@Override
	public void writeByteArray(byte[] bytes) {
		checkLimit(this,bytes.length);
		super.writeByteArray(bytes);
	}

	@Override
	public void writeCharArray(char[] chars) {
		checkLimit(this,chars.length*2);
		super.writeCharArray(chars);
	}

	@Override
	public void writeIntArray(int[] ints) {
		checkLimit(this,ints.length*4);
		super.writeIntArray(ints);
	}

	@Override
	public void writeLongArray(long[] longs) {
		checkLimit(this,longs.length*8);
		super.writeLongArray(longs);
	}

	@Override
	public void writeDoubleArray(double[] doubles) {
		checkLimit(this,doubles.length*8);
		super.writeDoubleArray(doubles);
	}

	@Override
	public void writeFloatArray(float[] floats) {
		checkLimit(this,floats.length*4);
		super.writeFloatArray(floats);
	}

	@Override
	public void writeShortArray(short[] shorts) {
		checkLimit(this,shorts.length*2);
		super.writeShortArray(shorts);
	}

	@Override
	public void writeBooleanArray(boolean[] booleans) {
		checkLimit(this,booleans.length);
		super.writeBooleanArray(booleans);
	}

	@Override
	public void writeUTFArray(String[] utfs) {
		int i = utfs.length;
		while (--i>=0) {
			checkLimit(this,utfs[i].length()*6); //Estimate (maximum length)
		}
		super.writeUTFArray(utfs);
	}

	@Override
	public Appendable append(CharSequence csq) {
		checkLimit(this,csq.length()*6); //Estimate (maximum length)
		return super.append(csq);
	}

	@Override
	public Appendable append(CharSequence csq, int start, int end) {
		checkLimit(this,(end-start) * 6); //Estimate (maximum length)
		return super.append(csq, start, end);
	}

	@Override
	public Appendable append(char c) {
		checkLimit(this, 6); //Estimate (maximum length)
		return super.append(c);
	}


}
