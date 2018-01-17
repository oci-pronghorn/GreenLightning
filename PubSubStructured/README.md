# What you will need before you start:
-[Java 8](https://docs.oracle.com/javase/8/docs/technotes/guides/install/install_overview.html) 

-[Maven](https://maven.apache.org/install.html), which downloads and manages the libraries and APIs needed to get the Grove device working.

-[Git](https://git-scm.com/), which clones a template Maven project with the necessary dependencies already set up.

# Starting your Maven project: 
[Starting a mvn project](https://github.com/oci-pronghorn/FogLighter/blob/master/README.md)

# Example project:

The following sketch will demonstrate a basic demo for using a PubSub Structured.

Demo code:


```java
package com.ociweb.oe.greenlightning.api;

import com.ociweb.gl.api.Builder;
import com.ociweb.gl.api.GreenApp;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.pronghorn.util.AppendableProxy;
import com.ociweb.pronghorn.util.Appendables;

public class PubSubStructured implements GreenApp
{
    static int VALUE_FIELD = 1;
    static int SENDER_FIELD = 2;
    
    private AppendableProxy console;
    private AppendableProxy console2;
    
    public PubSubStructured(Appendable console, Appendable console2) {
    	this.console = Appendables.proxy(console);
    	this.console2 = Appendables.proxy(console2);
    }
    
    @Override
    public void declareConfiguration(Builder c) {
    }

    @Override
    public void declareBehavior(GreenRuntime runtime) {

        runtime.addStartupListener(new KickoffBehavior(runtime, "topicOne"));

        runtime.addPubSubListener(new IncrementValueBehavior(runtime, "topicTwo", console))
                                                  .addSubscription("topicOne");

        runtime.addPubSubListener(new IncrementValueBehavior(runtime, "topicOne", console))
                                                  .addSubscription("topicTwo");

        runtime.addPubSubListener(new FilterValueBehavior(runtime, "filteredValues"))
                                                  .addSubscription("topicOne")
                                                  .addSubscription("topicTwo");
                
        runtime.addPubSubListener(new ConsoleWrite(console2))
        										  .addSubscription("filteredValues");
        
    
    }
}
```


Behavior class:


```java
package com.ociweb.oe.greenlightning.api;

import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.StartupListener;

public class KickoffBehavior implements StartupListener {
	private final GreenCommandChannel cmd;
	private final CharSequence publishTopic;

	KickoffBehavior(GreenRuntime runtime, CharSequence publishTopic) {
		this.cmd = runtime.newCommandChannel(DYNAMIC_MESSAGING);
		this.publishTopic = publishTopic;
	}

	@Override
	public void startup() {
		// Send the initial value on startup
		cmd.presumePublishStructuredTopic(publishTopic, writer -> {
			writer.writeUTF8(PubSubStructured.SENDER_FIELD, "from kickoff behavior");
			writer.writeLong(PubSubStructured.VALUE_FIELD, 1);
		});
	}
}
```


#### ERROR:  could not read file ./src/main/java/com/ociweb/oe/greenlightning/api/DecrementValueBehavior.java


```java
package com.ociweb.oe.greenlightning.api;

import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.PubSubListener;
import com.ociweb.pronghorn.pipe.ChannelReader;
import com.ociweb.pronghorn.util.field.MessageConsumer;

public class ThingBehavior implements PubSubListener {

	private final GreenCommandChannel cmd;
    private final MessageConsumer consumer;
    private int lastValue;
    private final CharSequence publishTopic;
    private final GreenRuntime runtime;
		
    public ThingBehavior(GreenRuntime runtime, CharSequence topic) {
    	this.cmd = runtime.newCommandChannel(DYNAMIC_MESSAGING);

		this.consumer = new MessageConsumer()
				            .integerProcessor(PubSubStructured.VALUE_FIELD, 
				            		(value)->{
				            			lastValue = (int)value;
				            			return true;
				            	});
		
		this.publishTopic = topic;
		this.runtime = runtime;
	}
    
    
	@Override
	public boolean message(CharSequence topic, ChannelReader payload) {
					
		//
		////NOTE: this one line will copy messages from payload if consumer returns true
		////      when the message is copied its topic is changed to the first argument string
		//
		//cmd.copyStructuredTopic("outgoing topic", payload, consumer);
		//
		
		if (consumer.process(payload)) {
			if (lastValue>0) {
				System.out.println(lastValue);
				return cmd.publishStructuredTopic(publishTopic, (writer)->{
					writer.writeLong(PubSubStructured.VALUE_FIELD, lastValue-1);
		    		writer.writeUTF8(PubSubStructured.SENDER_FIELD, "from thing one behavior");					
				});
			} else {
				runtime.shutdownRuntime();
				return true;
			} 
		} else {
			return false;
		}
		
	}

}
```



This class is a simple demonstration of PubSub Structured. While similar to the normal PubSub, PubSub Structured is meant for larger messages instead of just simpler ones. 
