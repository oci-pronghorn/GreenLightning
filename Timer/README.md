# What you will need before you start:
-[Java 8](https://docs.oracle.com/javase/8/docs/technotes/guides/install/install_overview.html) 

-[Maven](https://maven.apache.org/install.html), which downloads and manages the libraries and APIs needed to get the Grove device working.

-[Git](https://git-scm.com/), which clones a template Maven project with the necessary dependencies already set up.

# Starting your Maven project: 
[Starting a mvn project](https://github.com/oci-pronghorn/FogLighter/blob/master/README.md)

# Example project:

The following sketch will demonstrate two simple uses of the addTimeListener() method.

Demo code: 


```java
package com.ociweb.oe.greenlightning.api;

import com.ociweb.gl.api.*;
import com.ociweb.pronghorn.util.AppendableProxy;
import com.ociweb.pronghorn.util.Appendables;

public class Timer implements GreenApp
{
	private final AppendableProxy console;
	private final int rate;
		
	public Timer(Appendable console, int rate) {
		this.console = Appendables.proxy(console);
		this.rate = rate;
	}
	
    @Override
    public void declareConfiguration(Builder config) {
    	config.setTimerPulseRate(rate); //the rate at which time is checked in milliseconds 
    }

    @Override
    public void declareBehavior(GreenRuntime runtime) {
    	runtime.addTimePulseListener(new TimeBehavior(runtime, console));
    }
}
```


The first demo in this code uses the addTimeListener() method to print out the string "clock" at the top of every minute, regardless of when the program was started. The second demo uses the addTimeListener() method to print out the string "clock" at an interval of one minute since the start of the program. You can change the interval length by changing timeInterval .
