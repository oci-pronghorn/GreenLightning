package com.ociweb.gl.example.personApp;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPRequestReader;
import com.ociweb.gl.api.PubSubService;
import com.ociweb.gl.api.RestListener;
import com.ociweb.pronghorn.pipe.StructuredReader;
import com.ociweb.pronghorn.pipe.StructuredWriter;

public class PersonAdmin implements RestListener {

	private PubSubService pubService;

	public PersonAdmin(GreenRuntime runtime) {
		pubService = runtime.newCommandChannel().newPubSubService();
	}

	@Override
	public boolean restRequest(HTTPRequestReader request) {
		
		if (request.isVerbPost()) {
			
			return pubService.publishTopic(GreenStructInternal.adminPersons.name()+"add", 
					w->{
						
						long con = request.getConnectionId();
						long seq = request.getSequenceCode();
						
						StructuredWriter output = w.structured();
												
						output.writeLong(GreenField.connectionId, con);
						output.writeLong(GreenField.connectionId, seq);
						
						StructuredReader input = request.structured();
												
						input.readLong(GreenField.id, output);
						input.readInt(GreenField.age, output);
						input.readText(GreenField.firstName, output);
						input.readText(GreenField.lastName, output);
						input.readBoolean(GreenField.enabled, output);
						
						output.selectStruct(GreenStructInternal.adminPersons);				
						
					});
			
		} else {
			return pubService.publishTopic(GreenStructInternal.adminPersons.name()+"dump");
		}		
		
		
	}

}
