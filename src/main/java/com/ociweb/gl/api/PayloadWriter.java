package com.ociweb.gl.api;

import java.io.IOException;

import com.ociweb.gl.impl.BuilderImpl;
import com.ociweb.pronghorn.pipe.DataInputBlobReader;
import com.ociweb.pronghorn.pipe.DataOutputBlobWriter;
import com.ociweb.pronghorn.pipe.MessageSchema;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeWriter;

public class PayloadWriter<T extends MessageSchema<T>> extends DataOutputBlobWriter<T> {

    private final Pipe<T> p;
    private final int maxLength;
    private int length;
    private GreenCommandChannel commandChannel;
    private final int goIndex;
    private long key;
    private int loc=-1;
    
    protected PayloadWriter(Pipe<T> p, int goIndex) {
    	
    	super(p);
    	this.p = p;    
    	this.maxLength = p.maxVarLen;
    	this.goIndex = goIndex;
        assert(goIndex>=0);
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
    	if (loc!=-1) {
    		publish();
    	}
    	
    	try {
			super.close();
		} catch (IOException e) {
			throw new RuntimeException();
		}
    	
    }
    
    //TODO: add method for wait on publish until this time.
    //public void publish(AtTime time);
    

    public void publish() {
        if (loc!=-1) {
	        closeHighLevelField(loc);
	        loc = -1;//clear field
	        PipeWriter.publishWrites(p);     
	        
	        GreenCommandChannel.publishGo(1, 
	        		goIndex, 
	        		(GreenCommandChannel<?>) (GreenCommandChannel)commandChannel);
	        
        }
    }

    public void openField(int loc, GreenCommandChannel commandChannel) {
    	//assert(this.loc == -1) : "Already open for writing, can not open again.";
    	this.commandChannel = commandChannel;
        this.loc = loc;
        this.length = 0;
        DataOutputBlobWriter.openField(this);
    }

    private void checkLimit(PayloadWriter<T> that, int x) {
    	
    	if ( (that.length+=x) > that.maxLength ) {
    		throw new RuntimeException("This field is limited to a maximum length of "+that.maxLength+". Write less data or declare a larger max payload size.");
    	}
    	
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
