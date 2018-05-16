package com.ociweb.gl.api;

import com.ociweb.pronghorn.pipe.ChannelWriter;
import com.ociweb.pronghorn.pipe.DataInputBlobReader;
import com.ociweb.pronghorn.pipe.DataOutputBlobWriter;
import com.ociweb.pronghorn.pipe.MessageSchema;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeWriter;

public class PayloadWriter<T extends MessageSchema<T>> extends DataOutputBlobWriter<T> {

    private final Pipe<T> p;
    private MsgCommandChannel commandChannel;

    private int loc=-1;
    
    protected PayloadWriter(Pipe<T> p) {
    	
    	super(p);
    	this.p = p;    
    }

    public void writeString(CharSequence value) {
        writeUTF(value);
    }
    
    public void close() {
    	throw new UnsupportedOperationException();
    }
    
    //TODO: add method for wait on publish until this time.
    //public void publish(AtTime time);

	/**
	 *
	 * @return true if loc!= 1 else false
	 */
	public boolean publish() {
        if (loc!=-1) {
	        closeHighLevelField(loc);
	        loc = -1;//clear field
	        PipeWriter.publishWrites(p);     
	        return true;	       
	        
        }
        return false;
    }

	public void openField(int loc, MsgCommandChannel commandChannel) {
    	//assert(this.loc == -1) : "Already open for writing, can not open again.";
    	this.commandChannel = commandChannel;
        this.loc = loc;
        DataOutputBlobWriter.openField(this);
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
	public void writeUTFArray(String[] utfs) {
		int i = utfs.length;
		while (--i>=0) {
			checkLimit(this,utfs[i].length()*6); //Estimate (maximum length)
		}
		super.writeUTFArray(utfs);
	}

	@Override
	public ChannelWriter append(CharSequence csq) {
		checkLimit(this,csq.length()*6); //Estimate (maximum length)
		return super.append(csq);
	}

	@Override
	public ChannelWriter append(CharSequence csq, int start, int end) {
		checkLimit(this,(end-start) * 6); //Estimate (maximum length)
		return super.append(csq, start, end);
	}

	@Override
	public ChannelWriter append(char c) {
		checkLimit(this, 6); //Estimate (maximum length)
		return super.append(c);
	}


}
