package com.mydomain.greenlightning.slipstream;

import org.h2.tools.Server;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPRequestReader;
import com.ociweb.gl.api.PubSubFixedTopicService;
import com.ociweb.gl.api.RestMethodListener;
import com.ociweb.gl.api.ShutdownListener;
import com.ociweb.pronghorn.pipe.StructuredReader;
import com.ociweb.pronghorn.pipe.StructuredWriter;

public class ProductsBehavior implements RestMethodListener, ShutdownListener {

	private final PubSubFixedTopicService publishService;	
	private Server server;//held so we can shut down later.

	
	public ProductsBehavior(GreenRuntime runtime, int maxProductId, String topic, Server server) {
		this.server = server;
		this.publishService = runtime.newCommandChannel().newPubSubService(topic,4,400);

	}
		
	public boolean productUpdate(HTTPRequestReader request) {
		StructuredReader source = request.structured();
		
		return publishService.publishTopic((w)->{
			
			//write update message with new payload plus sequence and connectionID
			StructuredWriter target = w.structured();
			target.writeInt(Field.ID, source.readInt(Field.ID));
			target.writeInt(Field.QUANTITY, source.readInt(Field.QUANTITY));
			target.writeBoolean(Field.DISABLED, source.readBoolean(Field.DISABLED));
			
			//source.readText(Field.NAME, target.writeText(Field.NAME)); //Broken??
			target.writeText(Field.NAME, source.readText(Field.NAME));
			
			
			target.writeLong(Field.CONNECTION, request.getConnectionId());
			target.writeLong(Field.SEQUENCE, request.getSequenceCode());
			target.selectStruct(Struct.DB_PRODUCT_UPDATE);

		});
	}
	
	public boolean productQuery(HTTPRequestReader request) {
		StructuredReader source = request.structured();
		
		return publishService.publishTopic((w)->{
			
			StructuredWriter target = w.structured();
			target.writeInt(Field.ID, source.readInt(Field.ID));
			target.writeLong(Field.CONNECTION, request.getConnectionId());
			target.writeLong(Field.SEQUENCE, request.getSequenceCode());
			target.selectStruct(Struct.DB_PRODUCT_QUERY);
			
		});
	
	}
	
	public boolean productAll(HTTPRequestReader request) {

		return publishService.publishTopic((w)->{
			
			StructuredWriter target = w.structured();
			target.writeLong(Field.CONNECTION, request.getConnectionId());
			target.writeLong(Field.SEQUENCE, request.getSequenceCode());
			target.selectStruct(Struct.DB_ALL_QUERY);
			
		});

	}

	@Override
	public boolean acceptShutdown() {
		server.shutdown();
		return true;
	}
	
}
