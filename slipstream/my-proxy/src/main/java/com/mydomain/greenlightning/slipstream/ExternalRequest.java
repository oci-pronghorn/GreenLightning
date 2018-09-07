package com.mydomain.greenlightning.slipstream;

import com.ociweb.gl.api.ClientHostPortInstance;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPRequestReader;
import com.ociweb.gl.api.HTTPRequestService;
import com.ociweb.gl.api.PubSubFixedTopicService;
import com.ociweb.gl.api.RestListener;

public class ExternalRequest implements RestListener {

	private final HTTPRequestService clientService;
	private final ClientHostPortInstance session;
	private final PubSubFixedTopicService pubSubService;
	private final String path;

	public ExternalRequest(GreenRuntime runtime, 
			               ClientHostPortInstance session, String path,
			               String topic) {
	
		this.clientService = runtime.newCommandChannel().newHTTPClientService();
		this.pubSubService = runtime.newCommandChannel().newPubSubService(topic);
		this.session = session;
		this.path = path;
	}

	@Override
	public boolean restRequest(HTTPRequestReader request) {
		if (pubSubService.hasRoomFor(1)) {
			if (!clientService.httpGet(session, path)) {
				return false;//try again later
			};
			pubSubService.presumePublishTopic(w-> request.handoff(w) );		
			return true;
		} else {
			return false;
		}
	}

}
