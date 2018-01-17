# What you will need before you start:
-[Java 8](https://docs.oracle.com/javase/8/docs/technotes/guides/install/install_overview.html) 

-[Maven](https://maven.apache.org/install.html), which downloads and manages the libraries and APIs needed to get the Grove device working.

-[Git](https://git-scm.com/), which clones a template Maven project with the necessary dependencies already set up.

# Starting your Maven project: 
[Starting a mvn project](https://github.com/oci-pronghorn/FogLighter/blob/master/README.md)

# Example project:

The following sketch will demonstrate a simple use of the ```addPubSubListener()``` method.

Demo code: 


```java
package com.ociweb.oe.greenlightning.api;

import com.ociweb.gl.api.Builder;
import com.ociweb.gl.api.GreenApp;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.pronghorn.util.AppendableProxy;
import com.ociweb.pronghorn.util.Appendables;


public class PubSub implements GreenApp
{
	private final AppendableProxy target;
	private final int seed;
	
	public PubSub(Appendable target, int seed) {
		this.target = Appendables.proxy(target);
		this.seed = seed;
	}
	
    @Override
    public void declareConfiguration(Builder c) {
        //no connections are needed
    }

    @Override
    public void declareBehavior(GreenRuntime runtime) {

    	runtime.addStartupListener(new KickoffBehavior(runtime, target));
    	
    	runtime.addPubSubListener(new GenerateBehavior(runtime, "Count", target, seed))
    	                     .addSubscription("Next");
    	
    	CountBehavior counter = new CountBehavior(runtime, "Next");
		runtime.registerListener(counter)
		           			.addSubscription("Count", counter::triggerNextAndCount)
		           			.addSubscription("AnExample", counter::anotherMessage);
    	
    	
    	
    }
          
}
```


The above code will generate seven random, lucky numbers. The first ```addPubSubListener()``` will generate a random number and add it to ArrayList ```luckyNums```. Once that has occured, it will publish a message uner the topic of "Gen", which the second PubSubListener is subscribed to, meaning that it is always listening for any publication under that topic. The second PubSubListener will simply print out the newest lucky number, then publish a message under the topic of "Print", which the first PubSubListener is subscribed to, restarting the process for a total of seven rounds.
