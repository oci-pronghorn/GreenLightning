package com.ociweb.gl.example.personApp;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPRequestReader;
import com.ociweb.gl.api.PubSubService;
import com.ociweb.gl.api.RestListener;
import com.ociweb.pronghorn.pipe.StructuredWriter;

public class PersonQuery implements RestListener {

	private final PubSubService pubService;

	public PersonQuery(GreenRuntime runtime) {
		pubService = runtime.newCommandChannel().newPubSubService();
	}

	@Override
	public boolean restRequest(HTTPRequestReader request) {
		return pubService.publishTopic(GreenStructInternal.queryPersonsList.name(),
                (w)->{
                	
                	StructuredWriter structured = w.structured();
					
                	structured.writeLong(GreenField.connectionId,
							   request.getConnectionId());
					
					structured.writeLong(GreenField.sequenceId, 
							   request.getSequenceCode());			
					
					structured.writeBoolean(GreenField.enabled, 
							   request.structured().isEqual(GreenField.enabled, "e".getBytes()));
					
					structured.selectStruct(GreenStructInternal.queryPersonsList);
					
                }
               );
	}

}
