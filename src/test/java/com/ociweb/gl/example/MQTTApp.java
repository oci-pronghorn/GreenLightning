package com.ociweb.gl.example;

import com.ociweb.gl.api.Builder;
import com.ociweb.gl.api.GreenApp;
import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.MQTTConfig;
import com.ociweb.gl.api.MessageReader;
import com.ociweb.gl.api.MsgRuntime;
import com.ociweb.gl.api.PubSubListener;
import com.ociweb.gl.api.PubSubWritable;
import com.ociweb.gl.api.PubSubWriter;
import com.ociweb.gl.api.TimeListener;

public class MQTTApp implements GreenApp {

	private MQTTConfig mqttConfig;
	
	//monitor    mosquitto_sub -v -t '#' -h 127.0.0.1
	
	public static void main( String[] args ) {
        MsgRuntime.run(new MQTTApp());
    }
		
	@Override
	public void declareConfiguration(Builder builder) {
		
		mqttConfig = builder.useMQTT("127.0.0.1", 1883, "my name")
							.cleanSession(true)
							.keepAliveSeconds(20);
		
		builder.setTriggerRate(1000);
		builder.enableTelemetry(true); //TODO: we see no MQTT in the graph..
		
	}

	@Override
	public void declareBehavior(GreenRuntime runtime) {
				
		runtime.subscriptionBridge("topic/ingress", mqttConfig); //optional 2 topics, optional transform lambda
		runtime.transmissionBridge("topic/egress", mqttConfig); //optional 2 topics, optional transform lambda
		
		final GreenCommandChannel cmdChnl = runtime.newCommandChannel(DYNAMIC_MESSAGING);		
		TimeListener timeListener = new TimeListener() {
			@Override
			public void timeEvent(long time, int iteration) {
				PubSubWritable writable = new PubSubWritable() {
					@Override
					public void write(PubSubWriter writer) {
						
						writer.writeUTF8Text("egress body");
						
						System.err.println("publish");
					}
				};
				cmdChnl.publishTopic("topic/egress", writable);
			}
		};
		runtime.addTimeListener(timeListener);
		
		
		PubSubListener listener = new PubSubListener() {
			@Override
			public boolean message(CharSequence topic, MessageReader payload) {
				
				String body = payload.readUTF();
				System.out.println("ingress body: "+body);
				
				return true;
			}
		};
		runtime.addPubSubListener(listener ).addSubscription("topic/ingress");
				
	}
}
