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
		
		///////////////////////////
		//NOTE: the parallel tracks by default will limit the scope of 
		//any pub/sub topic to inside that track.  The same topic usages
		//found in declareBehavior will also be isolated to those 
		//inside declareBehavior only.
		//NOTE: to create a message which spans between parallel
		//tracks and declareBehavior it must be done with a topic
		//defined as UnScoped wit the method defineUnScopedTopic.
		//NOTE: UnScoped topics are never supported as private topics 
		//since they require a router to manage the interTrack communication.	
		///////////////////////////
				
		builder.parallelTracks(4);
		
		builder.defineRoute().path("/test").routeId();
		
		//TODO: if the responder is found in the parallel section then mutate the name.
		builder.definePrivateTopic("/send/200", "consumer", "responder");

		//parallel looks right just does not yet do private topics.
		
		builder.usePrivateTopicsExclusively();
		
	//	builder.defineUnScopedTopic("/test/gobal");//not sure this is possible?
		
		////////////////////////////////////
		//add values on subcription
		//add values on command channel publish
	
		
		//let all track scoped topics publish to router 
		
		
		
	}

	@Override
	public void declareBehavior(GreenRuntime runtime) {
	//	runtime.addPubSubListener("watcher",new Watcher(runtime))
		//       .addSubscription("/test/gobal");
	}

	@Override
	public void declareParallelBehavior(GreenRuntime runtime) {

		runtime.addRestListener("consumer",new RestConsumer(runtime))
		       .includeAllRoutes();
		runtime.addPubSubListener("responder",new RestResponder(runtime))
		       .addSubscription("/send/200"); //add boolean for unscoped if required

	}
}
