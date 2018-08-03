package com.ociweb.gl.example.blocking;

import com.ociweb.gl.api.Builder;
import com.ociweb.gl.api.GreenAppParallel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPResponseService;
import com.ociweb.gl.api.PubSubFixedTopicService;
import com.ociweb.gl.api.PubSubService;
import com.ociweb.json.JSONExtractorImpl;
import com.ociweb.json.JSONExtractorCompleted;
import com.ociweb.json.JSONType;
import com.ociweb.pronghorn.network.config.HTTPHeaderDefaults;
import com.ociweb.pronghorn.struct.StructType;

public class BlockingExampleApp implements GreenAppParallel {

	
	private boolean telemetry;
	
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

		builder.defineRoute().parseJSON()
					.stringField("key1", Field.KEY1)
					.integerField("key2", Field.KEY2)
		       .path("/test")
			   .routeId(Struct.ROUTE);
		
		builder.usePrivateTopicsExclusively();			

		builder.defineStruct()
				.addField("connectionId", StructType.Long, 0, Field.CONNECTION_ID)
				.addField("sequenceId", StructType.Long, 0, Field.SEQUENCE_ID)
				.addField("key1", StructType.Text, 0, Field.KEY1)
			    .addField("key2", StructType.Integer, 0, Field.KEY2)		   
		        .register(Struct.DATA);			
		
	}

	@Override
	public void declareBehavior(GreenRuntime runtime) {
	}

	@Override
	public void declareParallelBehavior(GreenRuntime runtime) {
		
		PubSubFixedTopicService pub = runtime.newCommandChannel().newPubSubService("testTopicA");		
		runtime.addRestListener("restListener",(r)->{
			return pub.publishTopic((w)->{
				w.structured().writeLong(Field.CONNECTION_ID, r.getConnectionId());
				w.structured().writeLong(Field.SEQUENCE_ID, r.getSequenceCode());
				
				w.structured().writeInt(Field.KEY2, r.structured().readInt(Field.KEY2));
				w.structured().writeText(Field.KEY1,r.structured().readText(Field.KEY1));								
				
				w.structured().selectStruct(Struct.DATA); 
			});
		}).includeRoutesByAssoc(Struct.ROUTE); //TODO: need better error message when this is missing.
		
		//blocker will relay data\
		runtime.registerBlockingListener(
				()->{
					//Must be created new every time this lambda is called
					//This lambda is called once per thread on startup
					return new BlockingBehaviorExample();
				},
				Field.CONNECTION_ID, "testTopicA", "testTopicB");
		
		HTTPResponseService resp = runtime.newCommandChannel().newHTTPResponseService();		
		runtime.addPubSubListener("restResponder",(t,p)-> {
				
			return resp.publishHTTPResponse(
					p.structured().readLong(Field.CONNECTION_ID),
					p.structured().readLong(Field.SEQUENCE_ID),
					200);
		}).addSubscription("testTopicB"); 
		//TODO: add better message if this subscription is missing.
		
		

		
	}

}
