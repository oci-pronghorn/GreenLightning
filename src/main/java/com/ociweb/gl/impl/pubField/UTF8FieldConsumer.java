package com.ociweb.gl.impl.pubField;

import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeConfig;
import com.ociweb.pronghorn.pipe.RawDataSchema;

public class UTF8FieldConsumer<A extends Appendable> implements FieldConsumer {

	private UTF8FieldProcessor processor;
	private byte[] backing;
	private int position;
	private int length;
	private int mask;
	private A target;
	
    private byte primaryRingSizeInBits = 7; //this ring is 2^7 eg 128
    private byte byteRingSizeInBits = 16;
    private Pipe<RawDataSchema> pipe = new Pipe<RawDataSchema>(new PipeConfig(RawDataSchema.instance, primaryRingSizeInBits, byteRingSizeInBits));
	
	public UTF8FieldConsumer(UTF8FieldProcessor<A> processor, A target) {
		this.processor = processor;
		this.pipe.initBuffers();
		this.target = target;
		Pipe.validateVarLength(pipe, 10);		
	}
	
	public void store(long value) {	
	        
	        int size = Pipe.addMsgIdx(pipe, RawDataSchema.MSG_CHUNKEDSTREAM_1);
	        Pipe.addLongAsASCII(pipe, value);
	        Pipe.confirmLowLevelWrite(pipe, size);
	        Pipe.publishWrites(pipe);
			        
	        extractBytes();
	        
	}

	private void extractBytes() {
		Pipe.takeMsgIdx(pipe);
		int meta = Pipe.takeRingByteMetaData(pipe);
		
		length = Pipe.takeRingByteLen(pipe);
		backing = Pipe.byteBackingArray(meta, pipe);
		mask = Pipe.blobMask(pipe);
		position = Pipe.bytePosition(meta, pipe, length);
		
		Pipe.confirmLowLevelWrite(pipe);
		Pipe.publishWrites(pipe);
	}
	
	public void store(byte[] backing, int pos, int len, int mask) {
		this.backing = backing;
		this.position = pos;
		this.length = len;
		this.mask = mask;
	}
	
	public void store(byte e, long m) {
		
        int size = Pipe.addMsgIdx(pipe, RawDataSchema.MSG_CHUNKEDSTREAM_1);
        Pipe.addDecimalAsASCII(e, m, pipe);
        Pipe.confirmLowLevelWrite(pipe, size);
        Pipe.publishWrites(pipe);
		        
        extractBytes();
        
	}
	
	public void store(long numerator, long denominator) {

        int size = Pipe.addMsgIdx(pipe, RawDataSchema.MSG_CHUNKEDSTREAM_1);      
        Pipe.addRationalAsASCII(pipe, numerator, denominator);       
        Pipe.confirmLowLevelWrite(pipe, size);
        Pipe.publishWrites(pipe);
		        
        extractBytes();
        
	}
		
	public boolean run() {
		return processor.process(target);
	}
	
}
