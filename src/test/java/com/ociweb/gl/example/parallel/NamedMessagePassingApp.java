package com.ociweb.gl.example.parallel;

import com.ociweb.gl.api.Builder;
import com.ociweb.gl.api.GreenAppParallel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.json.JSONExtractorImpl;
import com.ociweb.json.JSONExtractorCompleted;
import com.ociweb.json.JSONType;
import com.ociweb.json.decode.JSONExtractor;
import com.ociweb.pronghorn.network.config.HTTPHeaderDefaults;

public class NamedMessagePassingApp implements GreenAppParallel {

	private boolean telemetry;
	private long rate;
	private long fieldA;
	private long fieldB;
		
    private boolean chunked = false;

	public enum Fields {nameA, nameB , urlArg;}
	
	
	public static void main(String[] args) {
		GreenRuntime.run(new NamedMessagePassingApp(false,4000));
	}
	
	public NamedMessagePassingApp(boolean telemetry, long rate) {
		this.telemetry = telemetry;
		this.rate = rate;
	}
	
	@Override
	public void declareConfiguration(Builder builder) {
		
		builder.useHTTP1xServer(8081)
		       .useInsecureServer()
		       //.logTraffic(false)
		       //TODO: confirm that 404 comes back when we get requests too large..
		       .setDecryptionUnitsPerTrack(2)
		       .setEncryptionUnitsPerTrack(2)
		       .setMaxRequestSize(1<<17)
		       .setMaxResponseSize(1<<16)
		       .setHost("127.0.0.1");		
		
		if (telemetry) {
			builder.enableTelemetry("127.0.0.1",8099);		
		}
				
//		ScriptedNonThreadScheduler.debugStageOrder = System.err;
		
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
				
		builder.parallelTracks(2); //TODO: mutiple tracks is breaking the auto private topic detection...
		
		builder.setDefaultRate(rate);

		
		// "{\"key1\":\"789\",\"key2\":123}";
		
		builder.setGlobalSLALatencyNS(50_000_000);
		
		
		JSONExtractor extractor =
				builder.defineJSONSDecoder().begin()
					.element(JSONType.TypeString).asField("key1",Fields.nameA)
		        	.element(JSONType.TypeInteger).asField("key2",Fields.nameB)
				.finish();
		
		
		int aRouteId = builder.defineRoute(extractor).path("/te${value}")
				        .associatedObject("value", Fields.urlArg)
				        .routeId();
		
		fieldA  = builder.lookupFieldByName(aRouteId,"nameA");
		fieldB  = builder.lookupFieldByName(aRouteId,"nameB");

		long fieldB2 = builder.lookupFieldByIdentity(aRouteId, Fields.nameB);
		assert(fieldB==fieldB2);
		long fieldL  = builder.lookupFieldByIdentity(aRouteId, HTTPHeaderDefaults.CONTENT_LENGTH);
		

		//not quite 2x slower for routed topics
		//	builder.defineUnScopedTopic("/send/200"); //TODO: urgent, this was required because for tracks this mangled topic name does not get matched...
	}

	@Override
	public void declareBehavior(GreenRuntime runtime) {
	
	//	runtime.addPubSubListener("watcher",new Watcher(runtime))
		//       .addSubscription("/test/gobal");
		
		
//		runtime.addRestListener("consumer",new RestConsumer(runtime, fieldA, fieldB, 
//				Fields.nameA,
//				Fields.nameB,
//				Fields.urlArg))
//		       .includeAllRoutes();
//
//		
//		runtime.addPubSubListener("responder",new RestResponder(runtime, chunked))
//		       .addSubscription("/send/200"); //add boolean for unscoped if required
		
	}

	@Override
	public void declareParallelBehavior(GreenRuntime runtime) {

		
//		GreenCommandChannel cmd = runtime.newCommandChannel(DYNAMIC_MESSAGING);		
//		runtime.addTimePulseListener((t,i)->{			
//		//	if (i ==10) {
//			//	cmd.publishTopic("helloWorld");
//			//}
//			
//		});

		runtime.addRestListener("consumer",new RestConsumer(runtime, fieldA, fieldB, 
				Fields.nameA,
				Fields.nameB,
				Fields.urlArg))
		       .includeAllRoutes();

		
		runtime.addPubSubListener("responder",new RestResponder(runtime, chunked))
		       .addSubscription("/send/200"); //add boolean for unscoped if required


	}
}
