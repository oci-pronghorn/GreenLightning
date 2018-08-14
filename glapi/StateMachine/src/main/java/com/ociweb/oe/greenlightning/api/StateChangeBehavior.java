package com.ociweb.oe.greenlightning.api;

import com.ociweb.gl.api.StateChangeListener;
import com.ociweb.oe.greenlightning.api.StateMachine.StopLight;
import com.ociweb.pronghorn.util.AppendableProxy;

public class StateChangeBehavior implements StateChangeListener<StopLight> {

	private final AppendableProxy console;
	
	public StateChangeBehavior(AppendableProxy console) {
		this.console = console;
	}

	@Override
	public boolean stateChange(StopLight oldState, StopLight newState) {
				
		console.append("                        It is time to ").append(newState.name()).append('\n');
		
		return true; //if we need to 'delay' the state change false can be returned.
	}

}
