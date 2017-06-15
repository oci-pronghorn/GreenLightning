package com.ociweb.gl.example;

import com.ociweb.gl.api.Builder;
import com.ociweb.gl.api.GreenApp;
import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.PubSubStructuredWritable;
import com.ociweb.gl.api.PubSubStructuredWriter;
import com.ociweb.gl.impl.pubField.IntFieldProcessor;
import com.ociweb.gl.impl.pubField.MessageConsumer;

public class MessageApp implements GreenApp { //TODO: this raw type is bad...

	
	public static void main( String[] args ) {
        GreenRuntime.run(new MessageApp());
    }
			
	@Override
	public void declareConfiguration(Builder builder) {
	}

	long globalValue = 100;
	long otherValue = 0;
	
	MessageConsumer consumer = new MessageConsumer()
			
			.add(1, new IntFieldProcessor() {
		
		@Override
		public boolean process(long value) {					
			globalValue = value-1;
			System.out.println(globalValue);
			return globalValue>0;
		}
		
	}); //TODO: not set up for lambda usage, add methods must be renamed.

	PubSubStructuredWritable writable = new PubSubStructuredWritable() {
		
		@Override
		public void write(PubSubStructuredWriter writer) {
			writer.writeLong(1, globalValue);
			
		}		
	};
	
	@Override
	public void declareBehavior(final GreenRuntime runtime) {
		
		//TODO CONSTANT IN WRONG PLACE, GCC ALSO SHOULD NOT BE GENERIC..
		final GreenCommandChannel gccA = runtime.newCommandChannel(GreenCommandChannel.DYNAMIC_MESSAGING);
		runtime.addPubSubListener((topic, payload)->{

		    if (consumer.process(payload)) {
		    	return gccA.openStructuredTopic("B", writable);
		    } else {
		    	runtime.shutdownRuntime();
		    	return true;
		    }
		}).addSubscription("A");
		
		
		final GreenCommandChannel gccB = runtime.newCommandChannel(GreenCommandChannel.DYNAMIC_MESSAGING);
		runtime.addPubSubListener((topic, payload)->{

		    if (consumer.process(payload)) {
		    	return gccB.openStructuredTopic("A", writable);
		    } else {
		    	runtime.shutdownRuntime();
		    	return true;
		    }
		}).addSubscription("B");
		
		final GreenCommandChannel gccC = runtime.newCommandChannel(GreenCommandChannel.DYNAMIC_MESSAGING);
		runtime.addStartupListener(()->{
			
			//TODO: add presumePublishStrucuredTopic...
			if (gccC.openStructuredTopic("A", writable)) {
				return;
			} else { 
				System.err.println("warning, we just started up yet we have no capacity for this");
				while (!gccC.openStructuredTopic("A", writable)) {
					Thread.yield();
				}
			}
		} 
		);
		
	}

}
