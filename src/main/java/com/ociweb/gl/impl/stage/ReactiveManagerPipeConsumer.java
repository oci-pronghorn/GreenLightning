package com.ociweb.gl.impl.stage;

import com.ociweb.gl.impl.TickListenerBase;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.stage.PronghornStage;

public class ReactiveManagerPipeConsumer {

	public final Pipe[] inputs;
	private final ReactiveOperator[] operators;
	public final Object behavior;
	

	public ReactiveManagerPipeConsumer(Object behavior, ReactiveOperators operators, Pipe[] inputs) {
		
		this.behavior = behavior;
		this.inputs = inputs;
		assert(PronghornStage.noNulls(inputs));
		this.operators = new ReactiveOperator[inputs.length];
		
		boolean doNotThrow = (behavior instanceof TickListenerBase);
		
		int i = inputs.length;
		while (--i>=0) {
			this.operators[i] = operators.getOperator(inputs[i], doNotThrow);	
		}

	}
	
	public static final void process(ReactiveManagerPipeConsumer that, ReactiveListenerStage r) {
		//only run if one of the inputs has received new data or have data.
		//if (that.newWork.getAndSet(false)) {			
			applyReactiveOperators(that, r, that.inputs, that.behavior, that.operators, that.inputs.length); 
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
				if (Pipe.isEmpty(localInputs[i]) || !Pipe.hasContentToRead(localInputs[i])) {
					//most calls are stopping on this if
				} else {
					if (null!=localOperators && null!=localOperators[i]) {//skip if null, this is for the TickListener
						localOperators[i].apply(i, localObj, localInputs[i], r);
						r.realStage.didWork();
						if (Pipe.hasContentToRead(localInputs[i])) {
							temp++;
							passes++;
						}
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

	/**
	 * A method used to switch old pipe with new pipe if true
	 * @param oldPipe Pipe arg used to determine swap
	 * @param newPipe Pipe arg used to determine swap
	 * @return true if --i> = 0 else false
	 */
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
