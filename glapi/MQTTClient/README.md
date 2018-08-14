# What you will need before you start:
-[Java 8](https://docs.oracle.com/javase/8/docs/technotes/guides/install/install_overview.html) 

-[Maven](https://maven.apache.org/install.html), which downloads and manages the libraries and APIs needed to get the Grove device working.

-[Git](https://git-scm.com/), which clones a template Maven project with the necessary dependencies already set up.

-[Mosquitto](https://mosquitto.org/download/), which is an MQTT message broker

# Starting your Maven project: 
[Starting a mvn project](https://github.com/oci-pronghorn/FogLighter/blob/master/README.md)

# Example project:

The following sketch will demonstrate a basic demo for using a MQTT.

Demo code:


```java
package com.ociweb.oe.greenlightning.api;

import com.ociweb.gl.api.*;
import com.ociweb.oe.greenlightning.api.behaviors.EgressBehavior;
import com.ociweb.oe.greenlightning.api.behaviors.IngressBehavior;
import com.ociweb.oe.greenlightning.api.behaviors.TimeBehavior;
import com.ociweb.pronghorn.stage.scheduling.ScriptedNonThreadScheduler;

public class MQTTClient implements GreenApp {
	private MQTTBridge mqttConfig;
	
	//install mosquitto - replace 127.0.0.1 if using a different broker
	//
	//to monitor call >    mosquitto_sub -v -t '#' -h 127.0.0.1
	//to test call >       mosquitto_pub -h 127.0.0.1 -t 'external/topic/ingress' -m 'hello'

	@Override
	public void declareConfiguration(Builder builder) {
		
		//TODO: the base is wrong for a script since they do not match total...
		
		ScriptedNonThreadScheduler.debug = false;
		
		//without low latency this PCT does not work..
		ScriptedNonThreadScheduler.lowLatencyEnforced = false;
		
		
		//final String brokerHost = "127.0.0.1"; //1883
		//final String brokerHost = "172.16.10.28"; // Nathan's PC
		//final String brokerHost = "thejoveexpress.local"; // Raspberry Pi0
		
		final String brokerHost = "test.mosquitto.org";
		final int port = 1883;//8883;
		
		// Create a single mqtt client
		mqttConfig = builder.useMQTT(brokerHost, port, "MQTTClientTest",200) //default of 10 in flight
							
			//	.useTLS()
							.cleanSession(true)
							.keepAliveSeconds(10)
							.lastWill("last/will", false, MQTTQoS.atLeastOnce, blobWriter -> {blobWriter.writeBoolean(true);});

		// Timer rate
		builder.setTimerPulseRate(1000); //once per second
		
		builder.enableTelemetry(8099);
	}

	@Override
	public void declareBehavior(final GreenRuntime runtime) {
		// The external/internal topic translation is not necessary.
		// The bridge calls may be made with one topic specified
		final String internalEgressTopic = "internal/topic/egress";
		final String externalEgressTopic = "external/topic/egress";
		final String internalIngressTopic = "internal/topic/ingress";
		final String externalIngressTopic = "external/topic/ingress";
		final String localTestTopic = "localtest";

		final MQTTQoS transQos = MQTTQoS.atLeastOnce;
		final MQTTQoS subscribeQos = MQTTQoS.atLeastOnce;

		// Inject the timer that publishes topic/egress
		TimeBehavior internalEgressTopicProducer = new TimeBehavior(runtime, internalEgressTopic);
		runtime.addTimePulseListener(internalEgressTopicProducer);
		// Convert the internal topic/egress to external for mqtt
		runtime.bridgeTransmission(internalEgressTopic, externalEgressTopic, mqttConfig).setQoS(transQos);
;
		// Subscribe to MQTT topic/ingress (created by mosquitto_pub example in comment above)
		runtime.bridgeSubscription(internalIngressTopic, externalIngressTopic, mqttConfig).setQoS(subscribeQos);
		// Listen to internal/topic/ingress and publish localtest
		IngressBehavior mqttBrokerListener = new IngressBehavior(runtime, localTestTopic);
		runtime.registerListener(mqttBrokerListener)
				.addSubscription(internalIngressTopic, mqttBrokerListener::receiveMqttMessage);

		// Inject the listener for "localtest"
		EgressBehavior doTheBusiness = new EgressBehavior();
		runtime.registerListener(doTheBusiness)
				.addSubscription(localTestTopic, doTheBusiness::receiveTestTopic);
	}
}
```


Behavior class:


```java
package com.ociweb.oe.greenlightning.api.behaviors;

import java.util.Date;

import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.TimeListener;
import com.ociweb.gl.api.WaitFor;
import com.ociweb.gl.api.Writable;

public class TimeBehavior implements TimeListener {
	private int droppedCount = 0;
    private final GreenCommandChannel cmdChnl;
	private final String publishTopic;
	private long total = 0;

	public TimeBehavior(GreenRuntime runtime, String publishTopic) {
		cmdChnl = runtime.newCommandChannel(DYNAMIC_MESSAGING);
		this.publishTopic = publishTopic;
	}

	@Override
	public void timeEvent(long time, int iteration) {
		int i = 1;//iterations
		while (--i>=0) {
			Date d = new Date(System.currentTimeMillis());
			
			// On the timer event create a payload with a string encoded timestamp
			Writable writable = writer -> writer.writeUTF8Text("'MQTT egress body " + d + "'");
					
			// Send out the payload with thre MQTT topic "topic/egress"
			boolean ok = cmdChnl.publishTopic(publishTopic, writable, WaitFor.None);
			if (ok) {
				System.err.println("sent "+d+" total "+(++total));
			}
			else {
				droppedCount++;
				System.err.println("The system is backed up, dropped "+droppedCount);
			}
		}
	}
}
```



```java
package com.ociweb.oe.greenlightning.api.behaviors;

import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.PubSubMethodListener;
import com.ociweb.gl.api.WaitFor;
import com.ociweb.gl.api.Writable;
import com.ociweb.pronghorn.pipe.ChannelReader;

public class IngressBehavior implements PubSubMethodListener {
	private final GreenCommandChannel cmd;
	private final String publishTopic;

	public IngressBehavior(GreenRuntime runtime, String publishTopic) {
		cmd = runtime.newCommandChannel(DYNAMIC_MESSAGING);
		this.publishTopic = publishTopic;
	}

	public boolean receiveMqttMessage(CharSequence topic, ChannelReader payload) {
		// this received when mosquitto_pub is invoked - see MQTTClient
		System.out.print("\ningress body: ");

		// Read the message payload and output it to System.out
		payload.readUTFOfLength(payload.available(), System.out);
		System.out.println();

		// Create the on-demand mqtt payload writer
		Writable mqttPayload = writer -> writer.writeUTF("\nsecond step test message");

		// On the 'localtest' topic publish the mqtt payload
		cmd.publishTopic(publishTopic, mqttPayload, WaitFor.None);

		// We consumed the message
		return true;
	}
}
```



```java
package com.ociweb.oe.greenlightning.api.behaviors;

import com.ociweb.gl.api.PubSubMethodListener;
import com.ociweb.pronghorn.pipe.ChannelReader;

public class EgressBehavior implements PubSubMethodListener {

	public boolean receiveTestTopic(CharSequence topic, ChannelReader payload) {
		// topic is the MQTT topic
		// payload is the MQTT payload
		// this received when mosquitto_pub is invoked - see MQTTClient
		System.out.println("got topic "+topic+" payload "+payload.readUTF()+"\n");

		return true;
	}
}
```



This class is a simple demonstration of MQTT (Message Queue Telemetry Transport). A lightweight messaging protocal, it was inititially designed for constrained devices and low-bandwidth, high-latency or unreliable networks. This demo uses Mosquitto as a message broker, which means that the messages that are published will go through Mosquitto, which will send them to and subsrcibers of the topic. 
