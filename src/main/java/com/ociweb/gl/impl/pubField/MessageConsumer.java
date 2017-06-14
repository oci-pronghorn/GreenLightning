package com.ociweb.gl.impl.pubField;

import java.util.ArrayList;
import java.util.List;

import com.ociweb.pronghorn.pipe.DataInputBlobReader;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.token.TokenBuilder;
import com.ociweb.pronghorn.pipe.token.TypeMask;
import com.ociweb.pronghorn.pipe.util.hash.IntHashTable;
import com.ociweb.pronghorn.util.TrieParserReader;

public class MessageConsumer {

	List<FieldConsumer> list = new ArrayList<FieldConsumer>();
	TrieParserReader reader = new TrieParserReader();
	
	IntHashTable fieldIdToIndex = new IntHashTable(12);
	FieldConsumer[][] consumers;
	
	
	public void readField(DataInputBlobReader reader) {
		//These asserts are required to ensure no one refactors the TypeMask to modify 
		//the order value of these constants.
		assert(TypeMask.IntegerSigned   == 0x02);// integer
		assert(TypeMask.LongSigned      == 0x06);// integer
		assert(TypeMask.TextUTF8        == 0x0A);// bytes 
		assert(TypeMask.Decimal         == 0x0C);// decimal
		assert(TypeMask.DecimalOptional == 0x0D);// rational
		assert(TypeMask.ByteVector      == 0x0E);// bytes
		
		
		int token = DataInputBlobReader.readPackedInt(reader);
		
		int type = TokenBuilder.extractType(token);
		int fieldId = TokenBuilder.extractId(token);
		
		
		FieldConsumer consumer = null; //must look up consumers from fieldId...
				
		storeValue(reader, type, consumer);
		
	}

	private void storeValue(DataInputBlobReader reader, int type, FieldConsumer consumer) {
		//NB: must not use more than 3 conditionals to find any specific value.
		//NB: the order of these are from most common to least common
		if (type < 0x0B) {
			if (type < 0x0A) {
					//integers
				    consumer.store(DataInputBlobReader.readPackedLong(reader));
			} else {
				
				Pipe backingPipe = DataInputBlobReader.getBackingPipe(reader);
				short length = reader.readShort();				
				int position = reader.position();
				consumer.store(backingPipe.blobRing, position, length, backingPipe.blobMask);
				reader.skipBytes(length);
			}			
		} else {
			if (type == TypeMask.ByteVector) {
				// bytes	
				
				Pipe backingPipe = DataInputBlobReader.getBackingPipe(reader);
				short length = reader.readShort();				
				int position = reader.position();
				consumer.store(backingPipe.blobRing, position, length, backingPipe.blobMask);			
				reader.skipBytes(length);
			
			} else {
				if (type == TypeMask.Decimal) {
					//decimal
					consumer.store(reader.readByte(), DataInputBlobReader.readPackedLong(reader));
				} else {
					//rational
					consumer.store(DataInputBlobReader.readPackedLong(reader), DataInputBlobReader.readPackedLong(reader));
				}
			}
		}
	}
	
	public MessageConsumer add(int fieldId, BytesFieldProcessor processor) {
		
		//index field id to list.size();?
		list.add(new BytesFieldConsumer(processor));
		
		return this;
	}
	
    public MessageConsumer add(int fieldId, DecimalFieldProcessor processor) {
		
		//index field id to list.size();?
		list.add(new DecimalFieldConsumer(processor, reader));
		
		return this;
	}

    public MessageConsumer add(int fieldId, IntFieldProcessor processor) {
		
		//index field id to list.size();?
		list.add(new IntFieldConsumer(processor, reader));
		
		return this;
	}
    
    public MessageConsumer add(int fieldId, RationalFieldProcessor processor) {
		
		//index field id to list.size();?
		list.add(new RationalFieldConsumer(processor, reader));
		
		return this;
	}
	
}
