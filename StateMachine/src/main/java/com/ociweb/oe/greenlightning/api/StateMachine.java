package com.ociweb.oe.greenlightning.api;

import com.ociweb.gl.api.*;
import com.ociweb.oe.greenlightning.api.StateMachine.StopLight;

import com.ociweb.gl.api.StateChangeListener;

public class StateMachine implements GreenApp
{

	static String cGreen = "Green";
	static String cYellow = "Yellow";
	static String cRed = "Red";
	
	public enum StopLight{
		
		Go(cGreen), 
		Caution(cYellow), 
		Stop(cRed);
		
		private String color;
		
		StopLight(String lightColor){
			color = lightColor;
		}
		
		public String getColor(){
			return color;
		}
	}

	
    @Override
    public void declareConfiguration(Builder c) {
    	
    	c.startStateMachineWith(StopLight.Stop);
    	c.setTimerPulseRate(1);
    }

   
	@SuppressWarnings("unchecked")
	@Override
    public void declareBehavior(GreenRuntime runtime) {

        
        runtime.addTimePulseListener(new TimingBehavior(runtime));
		runtime.addStateChangeListener(new StateChangeBehavior());
    }
          
}
