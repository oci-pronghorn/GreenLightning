package com.ociweb.oe.greenlightning.api;

import com.ociweb.gl.api.StateChangeListener;
import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.oe.greenlightning.api.StateMachine.StopLight;

public class StateChangeBehavior implements StateChangeListener {


	@Override
	public boolean stateChange(Enum oldState, Enum newState) {
		
		System.out.println("The light has chnaged to " + ((StopLight) newState).getColor());
		return true;
	}

}
