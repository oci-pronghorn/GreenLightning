# What you will need before you start:
-[Java 8](https://docs.oracle.com/javase/8/docs/technotes/guides/install/install_overview.html) 

-[Maven](https://maven.apache.org/install.html), which downloads and manages the libraries and APIs needed to get the Grove device working.

-[Git](https://git-scm.com/), which clones a template Maven project with the necessary dependencies already set up.

# Starting your Maven project: 
[Starting a mvn project](https://github.com/oci-pronghorn/FogLighter/blob/master/README.md)

# Example project:

The following sketch will demonstrate a simple use of the ```StateChangeListener```.

Demo code:
Main Class


```java
package com.ociweb.oe.foglight.api;


import static com.ociweb.iot.grove.GroveTwig.*;

import com.ociweb.iot.maker.*;
import com.ociweb.oe.foglight.api.StateMachine.StopLight;

import static com.ociweb.iot.maker.Port.*;

import com.ociweb.gl.api.StateChangeListener;

public class StateMachine implements FogApp
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
    public void declareConnections(Hardware c) {
    	
    	c.startStateMachineWith(StopLight.Stop);
    	c.setTimerPulseRate(1);
    }

   
	@SuppressWarnings("unchecked")
	@Override
    public void declareBehavior(FogRuntime runtime) {

        
        runtime.addTimePulseListener(new TimingBehavior(runtime));
		runtime.addStateChangeListener(new StateChangeBehavior());
    }
          
}
```


Behavior classes


```java
package com.ociweb.oe.foglight.api;

import com.ociweb.gl.api.StateChangeListener;
import com.ociweb.iot.maker.FogCommandChannel;
import com.ociweb.iot.maker.FogRuntime;
import com.ociweb.oe.foglight.api.StateMachine.StopLight;

public class StateChangeBehavior implements StateChangeListener {


	@Override
	public boolean stateChange(Enum oldState, Enum newState) {
		
		System.out.println("The light has chnaged to " + ((StopLight) newState).getColor());
		return true;
	}

}
```



```java
package com.ociweb.oe.foglight.api;

import com.ociweb.gl.api.TimeListener;
import com.ociweb.iot.maker.FogCommandChannel;
import com.ociweb.iot.maker.FogRuntime;
import com.ociweb.oe.foglight.api.StateMachine.StopLight;

public class TimingBehavior implements TimeListener {
	private static long startTime;
	private static boolean haveStartTime = false;
	private static final long fullTime = 15_000; //time from one red light to the next in milliseconds
    final FogCommandChannel channel;

	public TimingBehavior(FogRuntime runtime) {
		channel = runtime.newCommandChannel(DYNAMIC_MESSAGING);

	}


	@Override
	public void timeEvent(long time, int iteration) {
		
		if((time-startTime)%fullTime == 5_000) {
			System.out.print("Go! ");
			channel.changeStateTo(StopLight.Go);
		}
		else if((time-startTime)%fullTime == 10_000) {
			System.out.print("Caution. ");
			channel.changeStateTo(StopLight.Caution);
		}
		else if((time-startTime)%fullTime == 0) {
			System.out.print("Stop! ");
			channel.changeStateTo(StopLight.Stop);
		}
		
		if(!haveStartTime) {
			startTime = time;
			haveStartTime = true;
		}
	}

}
```




These classes are a basic demo of how to use the ```StateChangeListener``` method. In the main class, a stop light is simulated with 3 different states, ```Go```, ```Caution```, and ```Stop```. In the ```declareConnections``` section, the stop light is initialized to the ```Stop``` state to begin with. If a state is initialized there, you use a ```changeState()``` in a StartupListener as the two will clash when starting the program, so you must use one or the other. In the ```TimeBehavior``` class, a TimeListener is being used to change the state the state of the stop light. Every 5 seconds, the state is changed to the next state in the progression. In the ```StateChangeBehavior``` class, there is a StateChangeListener. Whenever it hears a change in state, it will print the new states color and will return true.
