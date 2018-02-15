package com.ociweb.gl.example.parallel;

import com.ociweb.gl.api.Builder;
import com.ociweb.gl.api.GreenApp;
import com.ociweb.gl.api.GreenAppParallel;
import com.ociweb.gl.api.GreenRuntime;

public class NamedMessagePassingApp implements GreenAppParallel {

	@Override
	public void declareConfiguration(Builder builder) {

		builder.useHTTP1xServer(8080).useInsecureServer();		
		builder.enableTelemetry();		
		builder.parallelism(4);
		
		//TODO: if the responder is found in the parallel section then mutate the name.
//		builder.definePrivateTopic("/sent/200", "responder", "watcher");
//		builder.definePrivateTopic("/sent/200", "responder1", "watcher");
//		builder.definePrivateTopic("/sent/200", "responder2", "watcher");
//		builder.definePrivateTopic("/sent/200", "responder3", "watcher");
		
	//	builder.usePrivateTopicsExclusively();
		builder.defineUnScopedTopic("/test/sdf");
		
		//clone method used for private topic routes
		//cnl writes and subscribe in looop is appended unless its not parallal
		
		
		
	}

	@Override
	public void declareBehavior(GreenRuntime runtime) {
		runtime.addPubSubListener("watcher",new Watcher(runtime)).addSubscription("/sent/200");
	}

	@Override
	public void declareParallelBehavior(GreenRuntime runtime) {
				
		//any pub subs here on the same topic become private!
		
		//test this by moving watcher down to this section...
		
		runtime.addRestListener("responder",new RestConsumer(runtime)).includeAllRoutes();
		
		
	}

}
