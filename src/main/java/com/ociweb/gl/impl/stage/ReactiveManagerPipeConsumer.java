package com.ociweb.gl.impl.stage;

import com.ociweb.pronghorn.pipe.Pipe;

public class ReactiveManagerPipeConsumer {

	public final Pipe[] inputs;
	private final ReactiveOperator[] operators;
	
	public ReactiveManagerPipeConsumer(ReactiveOperators operators, Pipe[] inputs) {
		
		this.inputs = inputs;
		this.operators = new ReactiveOperator[inputs.length];
		
		int i = inputs.length;
		while (--i>=0) {
			this.operators[i] = operators.getOperator(inputs[i]);
		}
	}
	
	public void process(Object obj) {
		int i = inputs.length;
		while (--i>=0) {
			operators[i].apply(obj, inputs[i]);
		}
	}
	
}
