package com.ociweb.gl.example;

import com.ociweb.pronghorn.pipe.ChannelReader;
import com.ociweb.pronghorn.pipe.ChannelWriter;
import com.ociweb.gl.api.*;

public class MassiveBehavior implements GreenApp {

	public static void main(String[] args) {
		GreenRuntime.run(new MassiveBehavior());
	}
	
	@Override
	public void declareConfiguration(Builder builder) {
		builder.setTimerPulseRate(500);//1);//TimeTrigger.OnTheSecond);
		builder.enableTelemetry();

	}

	@Override
	public void declareBehavior(GreenRuntime runtime) {
		
		//runtime.addTimePulseListener(new stopperBehavior(runtime));
		
		int i = 7;
		while (--i>=0) {
			final GreenCommandChannel cmd = runtime.newCommandChannel(DYNAMIC_MESSAGING);
			final String topic = "topic"+i;
			final int value = i;
			
			final Writable writable = new Writable() {

				@Override
				public void write(ChannelWriter writer) {
					writer.writePackedInt(value);
				}
				
			};
			
			TimeListener pubs = new TimeListener() {

				@Override
				public void timeEvent(long time, int iteration) {
					if (!cmd.publishTopic(topic, writable)) {
						System.out.println("overloaded can not publish "+value);
					}
				}
				
			};
			runtime.addTimePulseListener(pubs);
			
			PubSubListener subs = new PubSubListener() {
				
				public boolean message(CharSequence topic, ChannelReader payload) {
					
					
					return true;
				}
			};
	
			runtime
			 .addPubSubListener(subs)
			 .addSubscription(topic);
			 
			
		}
	
	
	}

}
