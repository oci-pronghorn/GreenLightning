package com.ociweb.gl.impl.stage;

import com.ociweb.pronghorn.pipe.Pipe;

public interface ReactiveOperator {

	public void apply(Object target, Pipe input, ReactiveListenerStage operatorImpl);
	
}


