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
		
		ScriptedNonThreadScheduler.debugStageOrder = null;
	
		
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
		runtime.addTimePulseListener("mytime",internalEgressTopicProducer);
		// Convert the internal topic/egress to external for mqtt
		runtime.bridgeTransmission(internalEgressTopic, externalEgressTopic, mqttConfig).setQoS(transQos);
;
		// Subscribe to MQTT topic/ingress (created by mosquitto_pub example in comment above)
		runtime.bridgeSubscription(internalIngressTopic, externalIngressTopic, mqttConfig).setQoS(subscribeQos);
		// Listen to internal/topic/ingress and publish localtest
		IngressBehavior mqttBrokerListener = new IngressBehavior(runtime, localTestTopic);
		runtime.registerListener("mqttBrokerListener",mqttBrokerListener)
				.addSubscription(internalIngressTopic, mqttBrokerListener::receiveMqttMessage);

		// Inject the listener for "localtest"
		EgressBehavior doTheBusiness = new EgressBehavior();
		runtime.registerListener("EgressBehavior",doTheBusiness)
				.addSubscription(localTestTopic, doTheBusiness::receiveTestTopic);
	}
}
