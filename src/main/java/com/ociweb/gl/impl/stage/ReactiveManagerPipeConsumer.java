package com.ociweb.gl.impl.stage;

import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.stage.PronghornStage;

public class ReactiveManagerPipeConsumer {

	public final Pipe[] inputs;
	private final ReactiveOperator[] operators;
	public final Object obj;
	
	public ReactiveManagerPipeConsumer(Object obj, ReactiveOperators operators, Pipe[] inputs) {
		
		this.obj = obj;
		this.inputs = inputs;
		assert(PronghornStage.noNulls(inputs));
		this.operators = new ReactiveOperator[inputs.length];
		
		int i = inputs.length;
		while (--i>=0) {
			this.operators[i] = operators.getOperator(inputs[i]);
		}
	}
	
	public final void process(ReactiveListenerStage r) {
		applyReactiveOperators(r, inputs, obj, operators, inputs.length);
	}

	private static void applyReactiveOperators(ReactiveListenerStage r, Pipe[] localInputs, Object localObj,
			ReactiveOperator[] localOperators, int count) {
		
		int hasMoreWork;
		do {
			hasMoreWork = 0;
			int i = count;
			while (--i>=0) {
				Pipe localPipe = localInputs[i];
				if (Pipe.contentRemaining(localPipe)>0) {
					localOperators[i].apply(i, localObj, localPipe, r);
					if (Pipe.contentRemaining(localPipe)>0) {
						hasMoreWork++;
					}
				}			
			}
		} while (--hasMoreWork>=0);
	}

	public boolean swapIfFound(Pipe oldPipe, Pipe newPipe) {		
		int i = inputs.length;
		while (--i>=0) {
			if (inputs[i] == oldPipe) {
				inputs[i] = newPipe;
				return true;
			}
		}
		return false;
	}
	
}
