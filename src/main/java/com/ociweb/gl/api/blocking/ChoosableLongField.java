package com.ociweb.gl.api.blocking;

import com.ociweb.pronghorn.pipe.MessageSchema;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.StructuredReader;
import com.ociweb.pronghorn.stage.blocking.Choosable;

public class ChoosableLongField<T extends MessageSchema<T>> implements Choosable<T> {

	private final long fieldId;
	private final int choiceCount;
	private final int offsetToStream;
	
	public ChoosableLongField(long fieldId,
			                  int choiceCount,
			                  int offsetToStream) {
		this.fieldId = fieldId;
		this.choiceCount = choiceCount;
		this.offsetToStream = offsetToStream;
	}
	
	@Override
	public int choose(Pipe<T> pipe) {
		if (!Pipe.hasContentToRead(pipe)) {
			return -1;
		} else {
			StructuredReader reader = Pipe.peekInputStream(pipe, offsetToStream)
					                      .structured();
			long readLong = reader.readLong(fieldId);
			return ((int)readLong)%choiceCount;
		}
	}
}
