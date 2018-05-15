package com.ociweb.gl.example.blocking;

import com.ociweb.gl.api.Builder;
import com.ociweb.gl.api.GreenAppParallel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPResponseService;
import com.ociweb.gl.api.PubSubService;
import com.ociweb.json.JSONExtractorImpl;
import com.ociweb.json.JSONExtractorCompleted;
import com.ociweb.json.JSONType;
import com.ociweb.pronghorn.network.config.HTTPHeaderDefaults;
import com.ociweb.pronghorn.struct.StructType;

public class BlockingExampleApp implements GreenAppParallel {

	static final JSONExtractorCompleted extractor = 
			new JSONExtractorImpl()
			.newPath(JSONType.TypeString).completePath("key1","name_a", Fields.key1)
			.newPath(JSONType.TypeInteger).completePath("key2","name_b", Fields.key2);
	
	
	private boolean telemetry;
	//TODO: these two fields will be removed...
	private long chooserLongFieldId;
	
	public BlockingExampleApp(boolean telemetry) {
		this.telemetry = telemetry;
	}

	@Override
	public void declareConfiguration(Builder builder) {
		
		builder.useHTTP1xServer(8083)
	       .useInsecureServer()
	       .logTraffic()
	       .setDecryptionUnitsPerTrack(3)
	       .setEncryptionUnitsPerTrack(3)
	       .setHost("127.0.0.1");		
		
		builder.parallelTracks(2);
	
		if (telemetry) {
			builder.enableTelemetry("127.0.0.1",8093);	
			
		}


		//TODO: associate routes with enums as well as routeIds...
		//TODO: as long as the con/seq is recorded before the header add predefined fields for these??
		builder.defineRoute(extractor)
		       .path("/test")
			   .routeId(Structs.route);
		
		builder.usePrivateTopicsExclusively();			
		
//		//TODO: need better errors when these strings are wrong
		builder.definePrivateTopic("testTopicA", "restListener", "blocker");
		builder.definePrivateTopic("testTopicB", "blocker", "restResponder");
		
//		builder.definePrivateTopic("testTopicA", "restListener", "restResponder");
		
		int structId = builder.defineStruct()
				.addField("connectionId", StructType.Long, 0, Fields.connectionId)
				.addField("sequenceId", StructType.Long, 0, Fields.sequenceId)
			    //.addFields(extractor)  TODO: this would be nicer.
				.addField("key1", StructType.Text, 0, Fields.key1)
			    .addField("key2", StructType.Integer, 0, Fields.key2)		   
		        .register(Structs.data);			
		
		chooserLongFieldId = builder.lookupFieldByIdentity(structId, Fields.connectionId);
		
	}

	@Override
	public void declareBehavior(GreenRuntime runtime) {
	}

	@Override
	public void declareParallelBehavior(GreenRuntime runtime) {
		
		PubSubService pub = runtime.newCommandChannel().newPubSubService();		
		runtime.addRestListener("restListener",(r)->{
			return pub.publishTopic("testTopicA",(w)->{
				
				//System.err.println("reading data from "+r.getConnectionId()+":"+r.getSequenceCode());
				
				w.structured().writeLong(Fields.connectionId, r.getConnectionId());
				w.structured().writeLong(Fields.sequenceId, r.getSequenceCode());
				
				//TODO: w.copy(r); //new method to send all matching data?
				w.structured().writeInt(Fields.key2, r.structured().readInt(Fields.key2));
				
				//Not GC free, TODO: need to update...
				w.structured().writeText(Fields.key1,r.structured().readText(Fields.key1));
								
				
				w.structured().selectStruct(Structs.data); 
			});
		}).includeRoutesByAssoc(Structs.route); //TODO: need better error message when this is missing.
		
		int threadsCount = 16;
		long timeoutNS = 120_000_000_000L;
		
		//blocker will relay data
		runtime.registerBlockingListener("blocker",
				()->{
					return new BlockingBehaviorExample();
				},
				threadsCount, 
				timeoutNS, 
				chooserLongFieldId); //TODO: instead of this pass in chooser?
		
		HTTPResponseService resp = runtime.newCommandChannel().newHTTPResponseService();		
		runtime.addPubSubListener("restResponder",(t,p)-> {
				
			return resp.publishHTTPResponse(
					p.structured().readLong(Fields.connectionId),
					p.structured().readLong(Fields.sequenceId),
					200);
		}).addSubscription("testTopicA"); 
		//TODO: add better message if this subscription is missing.
		
		

		
	}

}
