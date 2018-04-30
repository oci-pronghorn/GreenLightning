package com.ociweb.gl.impl.file;

import com.ociweb.pronghorn.pipe.DataInputBlobReader;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeWriter;
import com.ociweb.pronghorn.stage.file.schema.PersistedBlobLoadConsumerSchema;
import com.ociweb.pronghorn.stage.file.schema.PersistedBlobStoreConsumerSchema;

public class SerialStoreConsumer {

	private final Pipe<PersistedBlobStoreConsumerSchema> target;

	/**
	 *
	 * @param target
	 */
	public SerialStoreConsumer(Pipe<PersistedBlobStoreConsumerSchema> target) {
		this.target = target;
	}
		
	public boolean replay() {		
		if (PipeWriter.tryWriteFragment(target, PersistedBlobStoreConsumerSchema.MSG_REQUESTREPLAY_6)) {
			PipeWriter.publishWrites(target);	
			return false;
		} else {
			return true;
		}
	}

	public boolean clear() {		
		if (PipeWriter.tryWriteFragment(target, PersistedBlobStoreConsumerSchema.MSG_CLEAR_12)) {
			PipeWriter.publishWrites(target);	
			return true;
		}  else {
			return false;
		}
	}

	public boolean release(long blockId) {		
		if (PipeWriter.tryWriteFragment(target, PersistedBlobStoreConsumerSchema.MSG_RELEASE_7)) {
			PipeWriter.writeLong(target,PersistedBlobStoreConsumerSchema.MSG_RELEASE_7_FIELD_BLOCKID_3, blockId);
			PipeWriter.publishWrites(target);
			return true;
		} else {
			return false;
		}
	}

	
}
