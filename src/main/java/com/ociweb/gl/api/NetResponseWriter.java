package com.ociweb.gl.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.pronghorn.network.ServerCoordinator;
import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;
import com.ociweb.pronghorn.network.config.HTTPRevisionDefaults;
import com.ociweb.pronghorn.network.http.AbstractRestStage;
import com.ociweb.pronghorn.network.schema.ServerResponseSchema;
import com.ociweb.pronghorn.pipe.DataInputBlobReader;
import com.ociweb.pronghorn.pipe.DataOutputBlobWriter;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.util.Appendables;

public class NetResponseWriter extends DataOutputBlobWriter<ServerResponseSchema> implements Appendable {

	private static final Logger logger = LoggerFactory.getLogger(NetResponseWriter.class);
	
    private int context;
    public int statusCode;
    private HTTPContentTypeDefaults contentType;
    
    public NetResponseWriter(Pipe<ServerResponseSchema> p) {    	
    	super(p);
    }
        
    public void writeString(CharSequence value) {
        writeUTF(value);
    }
    
   
    public void close() {
    	  	
    	  	new UnsupportedOperationException("this is not the method you are looking for.").printStackTrace();
    	  	System.exit(-1);
    	  	
    }

	private static void writeHeader(NetResponseWriter outputStream, final int headerBlobPosition, 
			                        final long positionOfLen,
									int statusCode, final int context, 
									HTTPContentTypeDefaults contentType, 
									int length, boolean chunked) {
		//logger.info("writing head at position {} ", headerBlobPosition);
		DataOutputBlobWriter.openFieldAtPosition(outputStream, headerBlobPosition);

		byte[] revisionBytes = HTTPRevisionDefaults.HTTP_1_1.getBytes();		
		byte[] etagBytes = null;//TODO: nice feature to add later		
		int connectionIsClosed = 1&(context>>ServerCoordinator.CLOSE_CONNECTION_SHIFT);
		
		AbstractRestStage.writeHeader(revisionBytes, statusCode, 0, etagBytes, null!=contentType?contentType.getBytes():null, 
					                  length, chunked, false, null, 0,0,0, outputStream, connectionIsClosed);

		int propperLength = DataOutputBlobWriter.length(outputStream);
		Pipe.validateVarLength(outputStream.getPipe(), propperLength);
		Pipe.setIntValue(propperLength, outputStream.getPipe(), positionOfLen); //go back and set the right length.
		outputStream.getPipe().closeBlobFieldWrite();

	}
    
    
    private void writeChunkPrefix(NetResponseWriter outputStream, 
    		                      int headerBlobPosition, 
    		                      long positionOfLen,
    		                      int len) {
    	
    	//logger.trace("writing chunk at position {} ", headerBlobPosition);
		DataOutputBlobWriter.openFieldAtPosition(outputStream, headerBlobPosition);

		//since block has return on the end we must subtract 2 from what we wrote
        final int adj = 2+(5*(1&(context>>ServerCoordinator.END_RESPONSE_SHIFT))); 	
        
    	Appendables.appendHexDigitsRaw(outputStream, len-adj);
    	outputStream.write(AbstractRestStage.RETURN_NEWLINE);
    	
		int propperLength = DataOutputBlobWriter.length(outputStream);
		Pipe.validateVarLength(outputStream.getPipe(), propperLength);
		Pipe.setIntValue(propperLength, outputStream.getPipe(), positionOfLen); //go back and set the right length.
		outputStream.getPipe().closeBlobFieldWrite();    	
	}
    
    public void publishWithHeader(int headerBlobPosition, 
            					  long positionOfLen) {
    	
    	//update the header to match length
    	boolean isChunked = 0 == (ServerCoordinator.END_RESPONSE_MASK&context);

    	int len = closeLowLevelField(this); //end of writing the payload    	
    	
    	Pipe.addIntValue(context, backingPipe);  //real context    	
    	Pipe.confirmLowLevelWrite(backingPipe);
    	   	
    	writeHeader(this, headerBlobPosition, positionOfLen, statusCode, context, contentType,
    			    len, isChunked);
    	
    	//now publish both header and payload
    	Pipe.publishWrites(backingPipe);
    	
    	//System.out.println("published with header "+backingPipe);
 
    }
    
    public void publishWithChunkPrefix(int headerBlobPosition, 
            						   long positionOfLen) {
    	
    	int len = closeLowLevelField(this); //end of writing the payload    	
    	
    	Pipe.addIntValue(context, backingPipe);  //real context    	
    	Pipe.confirmLowLevelWrite(backingPipe);
    	
    	writeChunkPrefix(this, headerBlobPosition, positionOfLen, len);
    	
    	//now publish both header and payload
    	Pipe.publishWrites(backingPipe);
 
    	//System.out.println("published with chunkPrefix "+backingPipe);
    }



	void openField(final int context) {
		this.context = context;
		DataOutputBlobWriter.openField(this);
	}
    
	   
    void openField(int statusCode, final int context, HTTPContentTypeDefaults contentType) {

    	this.statusCode = statusCode;
    	this.contentType = contentType;
    	
    	this.context = context;

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
    public void writeUTF8Text(CharSequence s) {
		checkLimit(this,s.length()*6);//Estimated (maximum length)
    	super.writeUTF8Text(s);
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
			checkLimit(this,utfs[i].length() * 6); //Estimate (maximum length)
		}
		super.writeUTFArray(utfs);
	}

	@Override
	public Appendable append(CharSequence csq) {
		checkLimit(this,csq.length() * 6); //Estimate (maximum length)
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
