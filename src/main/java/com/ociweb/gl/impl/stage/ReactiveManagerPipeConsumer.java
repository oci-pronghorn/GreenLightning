package com.ociweb.gl.impl.stage;

import java.util.concurrent.atomic.AtomicBoolean;

import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipePublishListener;
import com.ociweb.pronghorn.stage.PronghornStage;

public class ReactiveManagerPipeConsumer {

	public final Pipe[] inputs;
	private final ReactiveOperator[] operators;
	public final Object obj;
	
//	private AtomicBoolean newWork = new AtomicBoolean(true);
//	private final PipePublishListener newWorkListener = new PipePublishListener() {
//		@Override
//		public void published() {
//			newWork.set(true);
//		}
//	};
	

	public ReactiveManagerPipeConsumer(Object obj, ReactiveOperators operators, Pipe[] inputs) {
		
		this.obj = obj;
		this.inputs = inputs;
		assert(PronghornStage.noNulls(inputs));
		this.operators = new ReactiveOperator[inputs.length];
		
		int i = inputs.length;
		while (--i>=0) {
			this.operators[i] = operators.getOperator(inputs[i]);
		}
		
//		//for the inputs register the head listener
//		int j = inputs.length;
//		while (--j>=0) {
//			Pipe.addPubListener(inputs[j], newWorkListener);
//		}
	}
	
	public static final void process(ReactiveManagerPipeConsumer that, ReactiveListenerStage r) {
		//only run if one of the inputs has received new data or have data.
		//if (that.newWork.getAndSet(false)) {			
			applyReactiveOperators(that, r, that.inputs, that.obj, that.operators, that.inputs.length); 
		//}
	}

	private static void applyReactiveOperators(ReactiveManagerPipeConsumer that, ReactiveListenerStage r,
			Pipe[] localInputs, Object localObj, ReactiveOperator[] localOperators, int count) {
		int passes = 0;
		int countDown = -2;
		int temp = 0;
		
		do {
			temp = 0;
			int i = count;
			while (--i >= 0) {
				if (Pipe.contentRemaining(localInputs[i])<=0) {
					//most calls are stopping on this if
				} else {
					localOperators[i].apply(i, localObj, localInputs[i], r);
					if (Pipe.contentRemaining(localInputs[i])>0) {
						temp++;
						passes++;
					}
				}			
			}
			if (-2==countDown) {
				countDown = passes;
			}
		} while (--countDown>=0);
		
		//if (temp>0) {
		//	that.newWork.set(true);
		//}
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
