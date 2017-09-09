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
package com.ociweb.oe.foglight.api;

import com.ociweb.iot.maker.FogApp;
import com.ociweb.iot.maker.FogRuntime;
import com.ociweb.iot.maker.Hardware;

public class PubSubStructured implements FogApp
{
    static int COUNT_DOWN_FIELD = 1;
    static int SENDER_FIELD = 2;

    @Override
    public void declareConnections(Hardware c) {
    }

    @Override
    public void declareBehavior(FogRuntime runtime) {
        // On startup kick off behavior will send the first message containing the first "topicOne" value
        runtime.addStartupListener(new KickoffBehavior(runtime, "topicOne", 100));
        // DecrementValueBehavior 1 will process "topicOne" and send to "topicTwo"
        runtime.addPubSubListener(new DecrementValueBehavior(runtime, "topicTwo", 1)).addSubscription("topicOne");
        // DecrementValueBehavior 2 will process "topicTwo" and send to "topicOne"
        runtime.addPubSubListener(new DecrementValueBehavior(runtime, "topicOne", 1)).addSubscription("topicTwo");
        // The prcocess loop will end when value reaches 0 and a shutdown command is issued
    }
}
```


Behavior class:


```java
package com.ociweb.oe.foglight.api;

import com.ociweb.gl.api.StartupListener;
import com.ociweb.iot.maker.FogCommandChannel;
import com.ociweb.iot.maker.FogRuntime;

public class KickoffBehavior implements StartupListener {
	private final FogCommandChannel cmd;
	private final CharSequence publishTopic;
	private final long countDownFrom;

	KickoffBehavior(FogRuntime runtime, CharSequence publishTopic, long countDownFrom) {
		cmd = runtime.newCommandChannel(DYNAMIC_MESSAGING);
		this.publishTopic = publishTopic;
		this.countDownFrom = countDownFrom;
	}

	@Override
	public void startup() {
		// Send the initial value on startup
		cmd.presumePublishStructuredTopic(publishTopic, writer -> {
			writer.writeUTF8(PubSubStructured.SENDER_FIELD, "from kickoff behavior");
			writer.writeLong(PubSubStructured.COUNT_DOWN_FIELD, countDownFrom);
		});
	}
}
```



```java
package com.ociweb.oe.foglight.api;

import com.ociweb.gl.api.PubSubListener;
import com.ociweb.iot.maker.FogCommandChannel;
import com.ociweb.iot.maker.FogRuntime;
import com.ociweb.pronghorn.pipe.BlobReader;
import com.ociweb.pronghorn.util.field.MessageConsumer;

public class DecrementValueBehavior implements PubSubListener {
	private final FogCommandChannel channel;
    private final MessageConsumer consumer;
    private final CharSequence publishTopic;
    private final FogRuntime runtime;
    private final long decrementBy;

	private long lastValue;
		
    DecrementValueBehavior(FogRuntime runtime, CharSequence publishTopic, long decrementBy) {
    	this.channel = runtime.newCommandChannel(DYNAMIC_MESSAGING);

    	// Process each field in order. Return false to stop processing.
		this.consumer = new MessageConsumer()
				            .integerProcessor(PubSubStructured.COUNT_DOWN_FIELD, value -> {
								lastValue = (int) value;
								return true;
							});
		
		this.publishTopic = publishTopic;
		this.runtime = runtime;
		this.decrementBy = decrementBy;
	}

	@Override
	public boolean message(CharSequence topic, BlobReader payload) {
		//
		////NOTE: this one line will copy messages from payload if consumer returns true
		////      when the message is copied its topic is changed to the first argument string
		//
		//cmd.copyStructuredTopic(publishTopic, payload, consumer);
		//
		// consumer.process returns the process chain return value
		if (consumer.process(payload)) {
			if (lastValue>0) {
				// If not zero, republish the message
				System.out.println(lastValue);
				return channel.publishStructuredTopic(publishTopic, writer -> {
					writer.writeLong(PubSubStructured.COUNT_DOWN_FIELD, lastValue-decrementBy);
					writer.writeUTF8(PubSubStructured.SENDER_FIELD, "from thing one behavior");
				});
			} else {
				// When zero, shutdown the system
				runtime.shutdownRuntime();
				return true;
			} 
		} else {
			return false;
		}
	}
}
```



```java
package com.ociweb.oe.foglight.api;

import com.ociweb.gl.api.PubSubListener;
import com.ociweb.gl.api.PubSubStructuredWritable;
import com.ociweb.iot.maker.FogCommandChannel;
import com.ociweb.iot.maker.FogRuntime;
import com.ociweb.pronghorn.pipe.BlobReader;
import com.ociweb.pronghorn.util.field.IntegerFieldProcessor;
import com.ociweb.pronghorn.util.field.MessageConsumer;
import com.ociweb.pronghorn.util.field.StructuredBlobWriter;

public class ThingBehavior implements PubSubListener {

	private final FogCommandChannel cmd;
    private final MessageConsumer consumer;
    private int lastValue;
    private final CharSequence publishTopic;
    private final FogRuntime runtime;
		
    public ThingBehavior(FogRuntime runtime, CharSequence topic) {
    	this.cmd = runtime.newCommandChannel(DYNAMIC_MESSAGING);

		this.consumer = new MessageConsumer()
				            .integerProcessor(PubSubStructured.COUNT_DOWN_FIELD, processor);
		
		this.publishTopic = topic;
		this.runtime = runtime;
	}
    
    private final PubSubStructuredWritable writable = new PubSubStructuredWritable() {
    	@Override
    	public void write(StructuredBlobWriter writer) {
    		writer.writeLong(PubSubStructured.COUNT_DOWN_FIELD, lastValue-1);
    		writer.writeUTF8(PubSubStructured.SENDER_FIELD, "from thing one behavior");
    	}			
    };

    private final IntegerFieldProcessor processor = new IntegerFieldProcessor() {			
    	@Override
    	public boolean process(long value) {
    		lastValue = (int)value;
    		return true;
    	}
    };
    
	@Override
	public boolean message(CharSequence topic, BlobReader payload) {
					
		//
		////NOTE: this one line will copy messages from payload if consumer returns true
		////      when the message is copied its topic is changed to the first argument string
		//
		//cmd.copyStructuredTopic("outgoing topic", payload, consumer);
		//
		
		if (consumer.process(payload)) {
			if (lastValue>0) {
				System.out.println(lastValue);
				return cmd.publishStructuredTopic(publishTopic, writable);
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
