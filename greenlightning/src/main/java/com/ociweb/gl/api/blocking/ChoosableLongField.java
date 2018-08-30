package com.ociweb.gl.api.blocking;

import com.ociweb.pronghorn.pipe.DataInputBlobReader;
import com.ociweb.pronghorn.pipe.MessageSchema;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.StructuredReader;
import com.ociweb.pronghorn.stage.blocking.Choosable;

public class ChoosableLongField<T extends MessageSchema<T>> implements Choosable<T> {

	private final Object fieldIdAssoc;
	private final int choiceCount;
	
	public ChoosableLongField(Object fieldIdAssoc,
			                  int choiceCount) {
		this.fieldIdAssoc = fieldIdAssoc;
		this.choiceCount = choiceCount;
	}
	
	@Override
	public int choose(Pipe<T> pipe) {
		if (!Pipe.hasContentToRead(pipe)) {
			return -1;
		} else {
			DataInputBlobReader<T> peekInputStream = Pipe.peekInputStream(pipe, BlockableStageFactory.streamOffset(pipe));
			if (peekInputStream.isStructured()) {			
				StructuredReader reader = peekInputStream.structured();
				if (reader.hasAttachedObject(fieldIdAssoc)) {
					return ((int)reader.readLong(fieldIdAssoc))%choiceCount;
				} else {
					return -1;
				}
			} else {
				return -1;
			}
		}
	}
}
