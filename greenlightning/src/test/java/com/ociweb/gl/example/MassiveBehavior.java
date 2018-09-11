package com.ociweb.gl.example;

import com.ociweb.gl.api.GreenApp;
import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenFramework;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.PubSubListener;
import com.ociweb.gl.api.PubSubService;
import com.ociweb.gl.api.TimeListener;
import com.ociweb.gl.api.Writable;
import com.ociweb.pronghorn.pipe.ChannelReader;
import com.ociweb.pronghorn.pipe.ChannelWriter;

public class MassiveBehavior implements GreenApp {

	public static void main(String[] args) {
		GreenRuntime.run(new MassiveBehavior());
	}
	
	@Override
	public void declareConfiguration(GreenFramework builder) {
		builder.setTimerPulseRate(500);//1);//TimeTrigger.OnTheSecond);
		builder.enableTelemetry();

	}

	@Override
	public void declareBehavior(GreenRuntime runtime) {
		
		//runtime.addTimePulseListener(new stopperBehavior(runtime));
		
		int i = 7;
		while (--i>=0) {
			final GreenCommandChannel cmd = runtime.newCommandChannel();
			final PubSubService pubSubServce = cmd.newPubSubService();
			
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
					if (!pubSubServce.publishTopic(topic, writable)) {
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
