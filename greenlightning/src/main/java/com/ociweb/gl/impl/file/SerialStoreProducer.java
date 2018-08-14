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

	/**
	 *
	 * @param blockId long arg used in PipeWriter.writeLong
	 * @param backing byte[] arg used in PipeWriter.writeBytes
	 * @param position int arg used in PipeWriter.writeBytes
	 * @param length int arg used in PipeWriter.writeBytes
	 * @return if (PipeWriter.tryWriteFragment(target, PersistedBlobStoreProducerSchema.MSG_BLOCK_1)) true else false
	 */
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


	/**
	 *
	 * @param blockId long arg used in PipeWriter.writeLong
	 * @param w Writable arg used to write(stream)
	 * @return if (PipeWriter.tryWriteFragment(target, PersistedBlobStoreProducerSchema.MSG_BLOCK_1)) true else false
	 */
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
