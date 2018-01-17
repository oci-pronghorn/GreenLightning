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
package com.ociweb.oe.greenlightning.api;

import com.ociweb.gl.api.Builder;
import com.ociweb.gl.api.GreenApp;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.pronghorn.util.AppendableProxy;
import com.ociweb.pronghorn.util.Appendables;

public class StateMachine implements GreenApp
{

	public enum StopLight{
		
		Go("Green"), 
		Caution("Yellow"), 
		Stop("Red");
		
		private String color;
		
		StopLight(String lightColor){
			color = lightColor;
		}
		
		public String getColor(){
			return color;
		}
	}
	
	private final AppendableProxy console;
	private final int rate;
	
	public StateMachine(Appendable console, int rate) {
		this.console = Appendables.proxy(console);
		this.rate = rate;
	}
	
    @Override
    public void declareConfiguration(Builder c) {
    	
    	c.startStateMachineWith(StopLight.Stop);
    	c.setTimerPulseRate(rate);
    }

	@Override
    public void declareBehavior(GreenRuntime runtime) {
        
        runtime.addTimePulseListener(new TimingBehavior(runtime, console));
		runtime.addStateChangeListener(new StateChangeBehavior(console))
		                     .includeStateChangeTo(StopLight.Go);
		runtime.addStateChangeListener(new StateChangeBehavior(console))
		                     .includeStateChangeTo(StopLight.Stop);
				
		
    }
          
}
```


Behavior classes


```java
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
```



```java
package com.ociweb.oe.greenlightning.api;

import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.TimeListener;
import com.ociweb.oe.greenlightning.api.StateMachine.StopLight;
import com.ociweb.pronghorn.util.AppendableProxy;
import com.ociweb.pronghorn.util.Appendables;

public class TimingBehavior implements TimeListener {

	private static final long fullCycle = 20; //from one red light to the next in iterations
    
	private final GreenCommandChannel channel;
	private final AppendableProxy console;
	private final GreenRuntime runtime;

	public TimingBehavior(GreenRuntime runtime, AppendableProxy console) {
		this.channel = runtime.newCommandChannel(DYNAMIC_MESSAGING);
		this.console = console;
		this.runtime = runtime;
	}

	@Override
	public void timeEvent(long time, int iteration) {

		if(iteration%fullCycle == 0) {
			changeState(time, StopLight.Go);
		}
		else if(iteration%fullCycle == 8) {
			changeState(time, StopLight.Caution);
		}
		else if(iteration%fullCycle == 11) {
			changeState(time, StopLight.Stop);
		}
		
		if (iteration == (fullCycle*3)) {
			runtime.shutdownRuntime(7);
		}

	}

	private void changeState(long time, StopLight target) {
		if (channel.changeStateTo(target)) {
			console.append(target.getColor()).append(" ");
			Appendables.appendEpochTime(console, time).append('\n');
		} else {
			console.append("unable to send state change, to busy");
		}
	}

}
```




These classes are a basic demo of how to use the ```StateChangeListener``` method. In the main class, a stop light is simulated with 3 different states, ```Go```, ```Caution```, and ```Stop```. In the ```declareConnections``` section, the stop light is initialized to the ```Stop``` state to begin with. If a state is initialized there, you use a ```changeState()``` in a StartupListener as the two will clash when starting the program, so you must use one or the other. In the ```TimeBehavior``` class, a TimeListener is being used to change the state the state of the stop light. Every 5 seconds, the state is changed to the next state in the progression. In the ```StateChangeBehavior``` class, there is a StateChangeListener. Whenever it hears a change in state, it will print the new states color and will return true.
