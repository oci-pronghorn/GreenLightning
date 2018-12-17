package com.ociweb.gl.example.parallel;

import com.ociweb.gl.api.GreenApp;
import com.ociweb.gl.api.GreenFramework;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.pronghorn.network.config.HTTPHeaderDefaults;

public class NamedMessagePassingApp implements GreenApp {

	private boolean telemetry;
	private long rate;
	private long fieldA;
	private long fieldB;
		
	private int tracks;
    private boolean chunked = false;
    private int port;

	public enum Fields {nameA, nameB , urlArg;}
	
	
	public static void main(String[] args) {
		GreenRuntime.run(new NamedMessagePassingApp(true,4000,2, 8081));
	}
	
	public NamedMessagePassingApp(boolean telemetry, long rate, int tracks, int port) {
		this.telemetry = telemetry;
		this.rate = rate;
		this.tracks = tracks;
		this.port = port;
	}
	
	@Override
	public void declareConfiguration(GreenFramework builder) {
		
		builder.useHTTP1xServer(port, tracks, this::declareParallelBehavior)
		       .useInsecureServer()
		       //.logTraffic(false)
		       //TODO: confirm that 404 comes back when we get requests too large..
		       .setDecryptionUnitsPerTrack(2)
		       .setConcurrentChannelsPerDecryptUnit(2)
		       .setEncryptionUnitsPerTrack(2)
		       .setConcurrentChannelsPerEncryptUnit(2)
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
						
		builder.setDefaultRate(rate);
	
		int aRouteId = builder.defineRoute()
				.parseJSON()
						.stringField("key1",Fields.nameA)
			        	.integerField("key2",Fields.nameB)
				.path("/te${value}")
				        .associatedObject("value", Fields.urlArg)
				        .routeId();
		
		fieldA  = builder.lookupFieldByName(aRouteId,"nameA");
		fieldB  = builder.lookupFieldByName(aRouteId,"nameB");

		long fieldB2 = builder.lookupFieldByIdentity(aRouteId, Fields.nameB);
		assert(fieldB==fieldB2);
		long fieldL  = builder.lookupFieldByIdentity(aRouteId, HTTPHeaderDefaults.CONTENT_LENGTH);
	
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
