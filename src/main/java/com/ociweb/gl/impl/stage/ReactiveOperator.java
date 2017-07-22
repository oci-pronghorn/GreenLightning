package com.ociweb.gl.impl.stage;

import com.ociweb.pronghorn.pipe.MessageSchema;
import com.ociweb.pronghorn.pipe.Pipe;

public interface ReactiveOperator<T extends MessageSchema<T>> {

	public void apply(Object target, Pipe<T> input);
	
}


