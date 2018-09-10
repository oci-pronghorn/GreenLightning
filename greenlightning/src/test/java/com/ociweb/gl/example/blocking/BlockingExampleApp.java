package com.ociweb.gl.example.blocking;

import com.ociweb.gl.api.Builder;
import com.ociweb.gl.api.GreenApp;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPResponseService;
import com.ociweb.gl.api.PubSubFixedTopicService;
import com.ociweb.gl.api.blocking.BlockingBehavior;
import com.ociweb.gl.api.blocking.BlockingBehaviorProducer;
import com.ociweb.pronghorn.pipe.ChannelReader;
import com.ociweb.pronghorn.struct.StructType;

public class BlockingExampleApp implements GreenApp {

	
	private boolean telemetry;
	
	public BlockingExampleApp(boolean telemetry) {
		this.telemetry = telemetry;
	}

	@Override
	public void declareConfiguration(Builder builder) {
		
		builder.useHTTP1xServer(8083, 2, this::declareParallelBehavior)
	       .useInsecureServer()
	      // .logTraffic() will collect many files in local folder if on when developing.
	       .setDecryptionUnitsPerTrack(3)
	       .setEncryptionUnitsPerTrack(3)
	       .setHost("127.0.0.1");		
	
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
				new BlockingBehaviorProducer() {

					@Override
					public boolean unChosenMessages(ChannelReader reader) {				
						return true;
					}

					@Override
					public BlockingBehavior produce() {
						//Must be created new every time this lambda is called
						//This lambda is called once per thread on startup
						return new BlockingBehaviorExample();
					}
					
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
