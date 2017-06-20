package com.ociweb.gl.api;

import com.ociweb.gl.impl.schema.MessagePubSub;
import com.ociweb.pronghorn.pipe.DataOutputBlobWriter;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeWriter;
import com.ociweb.pronghorn.pipe.token.OperatorMask;
import com.ociweb.pronghorn.pipe.token.TokenBuilder;
import com.ociweb.pronghorn.pipe.token.TypeMask;

public class PubSubWriter extends PayloadWriter<MessagePubSub> implements PubSubStructuredWriter {

	public PubSubWriter(Pipe<MessagePubSub> p, int goIndex) {
		super(p,goIndex);
	}

	@Override
	public void writeInt(int fieldId, int value) {
		
		writePackedInt(this,TokenBuilder.buildToken(TypeMask.IntegerSigned, 
				                                    OperatorMask.Field_None, 
				                                    fieldId));
		writeShort(-1); //room for future field name
	    writePackedInt(this,value);
		
	}

	@Override
	public void writeLong(int fieldId, long value) {
		
		writePackedInt(this,TokenBuilder.buildToken(TypeMask.LongSigned, 
									                OperatorMask.Field_None, 
									                fieldId));
		writeShort(-1); //room for future field name
		writePackedLong(this,value);
	}
	
	@Override
	public void writeBytes(int fieldId, byte[] backing, int offset, int length) {
		
		writePackedInt(this,TokenBuilder.buildToken(TypeMask.ByteVector, 
				OperatorMask.Field_None, 
				fieldId));
		writeShort(-1); //room for future field name
		writeShort(backing.length);
		write(backing,offset,length);
		
	}

	@Override
	public void writeBytes(int fieldId, byte[] backing) {
		writePackedInt(this,TokenBuilder.buildToken(TypeMask.ByteVector, 
				OperatorMask.Field_None, 
				fieldId));
		writeShort(-1); //room for future field name
		writeShort(backing.length);
		write(backing);
	}
	
	@Override
	public void writeBytes(int fieldId, byte[] backing, int offset, int length, int mask) {
		
		writePackedInt(this,TokenBuilder.buildToken(TypeMask.ByteVector, 
                									OperatorMask.Field_None, 
                									fieldId));
		writeShort(-1); //room for future field name
		writeShort(length);
		write(backing, offset, length, mask);
		
	}

	@Override
	public void writeUTF8(int fieldId, CharSequence text, int offset, int length) {
		
		writePackedInt(this,TokenBuilder.buildToken(TypeMask.TextUTF8, 
									                OperatorMask.Field_None, 
									                fieldId));
		writeShort(-1); //room for future field name
		
		writeShort(length);
		DataOutputBlobWriter.encodeAsUTF8(this, text, offset, length);

	}

	@Override
	public void writeUTF8(int fieldId, CharSequence text) {
		
		writePackedInt(this,TokenBuilder.buildToken(TypeMask.TextUTF8, 
									                OperatorMask.Field_None, 
									                fieldId));
		writeShort(-1); //room for future field name		
		writeUTF(text);
	}


	@Override
	public void writeDecimal(int fieldId, byte e, long m) {
		writePackedInt(this,TokenBuilder.buildToken(TypeMask.Decimal, 
                OperatorMask.Field_None, 
                fieldId));
		writeShort(-1); //room for future field name
		writeByte(e);
		writePackedLong(this, m);
	}

	@Override
	public void writeDouble(int fieldId, double value, byte places) {
		
		writePackedInt(this,TokenBuilder.buildToken(TypeMask.Decimal, 
									                OperatorMask.Field_None, 
									                fieldId));
		writeShort(-1); //room for future field name
		writeByte(-places);
		writePackedLong((long)Math.rint(value*PipeWriter.powd[64+places]));

	}

	@Override
	public void writeFloat(int fieldId, float value, byte places) {
		
		writePackedInt(this,TokenBuilder.buildToken(TypeMask.Decimal, 
									                OperatorMask.Field_None, 
									                fieldId));
		writeShort(-1); //room for future field name
		writeByte(-places);
		writePackedLong((long)Math.rint(value*PipeWriter.powd[64+places]));
		
	}
	
	@Override
	public void writeRational(int fieldId, long numerator, long denominator) {
		//NB: the type DecimalOptional is used to indicate rational value since Nulls are never sent
		writePackedInt(this,TokenBuilder.buildToken(TypeMask.DecimalOptional, //NB: re-use of type for two purposes 
									                OperatorMask.Field_None, 
									                fieldId));
		writeShort(-1); //room for future field name
		writePackedLong(numerator);
		writePackedLong(denominator);
	}

	

}
