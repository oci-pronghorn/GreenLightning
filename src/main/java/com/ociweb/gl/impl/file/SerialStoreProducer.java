package com.ociweb.gl.impl.file;

import com.ociweb.gl.api.Writable;
import com.ociweb.pronghorn.pipe.DataOutputBlobWriter;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeWriter;
import com.ociweb.pronghorn.stage.file.schema.PersistedBlobStoreProducerSchema;

public class SerialStoreProducer {

	private final Pipe<PersistedBlobStoreProducerSchema> target;
	
	public SerialStoreProducer(Pipe<PersistedBlobStoreProducerSchema> target) {
		this.target = target;
	}
	
	public boolean store(long blockId, byte[] backing, int position, int length) {
		if (PipeWriter.tryWriteFragment(target, PersistedBlobStoreProducerSchema.MSG_BLOCK_1)) {
			
			PipeWriter.writeLong(target,PersistedBlobStoreProducerSchema.MSG_BLOCK_1_FIELD_BLOCKID_3, blockId);
			PipeWriter.writeBytes(target,PersistedBlobStoreProducerSchema.MSG_BLOCK_1_FIELD_BYTEARRAY_2, backing, position, length);
			PipeWriter.publishWrites(target);
			return true;
		} else {
			return false;
		}
	}
	
	
	public boolean store(long blockId, Writable w) {
		if (PipeWriter.tryWriteFragment(target, PersistedBlobStoreProducerSchema.MSG_BLOCK_1)) {
			PipeWriter.writeLong(target,PersistedBlobStoreProducerSchema.MSG_BLOCK_1_FIELD_BLOCKID_3, blockId);
					
			DataOutputBlobWriter<PersistedBlobStoreProducerSchema> stream = PipeWriter.outputStream(target);
			DataOutputBlobWriter.openField(stream);
			
			w.write(stream);
			
			DataOutputBlobWriter.closeHighLevelField(stream, PersistedBlobStoreProducerSchema.MSG_BLOCK_1_FIELD_BYTEARRAY_2);
			
			PipeWriter.publishWrites(target);
			return true;
		} else {
			return false;
		}
	}
	

}
