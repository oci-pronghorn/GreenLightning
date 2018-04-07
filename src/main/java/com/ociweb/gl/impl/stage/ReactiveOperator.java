package com.ociweb.gl.impl.stage;

import com.ociweb.pronghorn.pipe.Pipe;

public abstract class ReactiveOperator {
	
	public abstract void apply(int index, Object target, Pipe input, ReactiveListenerStage operatorImpl);
	
}


