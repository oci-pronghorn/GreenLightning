package com.ociweb.gl.example.parallel;

import java.io.File;
import java.io.IOException;

import com.ociweb.gl.api.Builder;
import com.ociweb.gl.api.GreenAppParallel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.json.JSONExtractor;
import com.ociweb.json.JSONExtractorCompleted;
import com.ociweb.json.JSONType;
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
				
		builder.parallelTracks(1);
		
		builder.setDefaultRate(rate);

		
		// "{\"key1\":\"789\",\"key2\":123}";
		
		builder.setGlobalSLALatencyNS(50_000_000);
		
		
		JSONExtractorCompleted extractor =
				builder.defineJSONExtractor()
				.newPath(JSONType.TypeString).key("key1").completePath("name_a", Fields.nameA)
		        .newPath(JSONType.TypeInteger).key("key2").completePath("name_b", Fields.nameB);
		
		
		int aRouteId = builder.defineRoute(extractor).path("/te${value}")
				        .associatedObject("value", Fields.urlArg)
				        .routeId();
		
		fieldA  = builder.lookupFieldByName(aRouteId,"name_a");
		fieldB  = builder.lookupFieldByName(aRouteId,"name_b");
		long fieldB2 = builder.lookupFieldByIdentity(aRouteId, Fields.nameB);
		assert(fieldB==fieldB2);
		long fieldL  = builder.lookupFieldByIdentity(aRouteId, HTTPHeaderDefaults.CONTENT_LENGTH);
		
		//TODO: if the responder is found in the parallel section then mutate the name.
		builder.definePrivateTopic(1<<16,100,"/send/200", "consumer", "responder");
		builder.usePrivateTopicsExclusively();

	}

	@Override
	public void declareBehavior(GreenRuntime runtime) {
	
	//	runtime.addPubSubListener("watcher",new Watcher(runtime))
		//       .addSubscription("/test/gobal");
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
