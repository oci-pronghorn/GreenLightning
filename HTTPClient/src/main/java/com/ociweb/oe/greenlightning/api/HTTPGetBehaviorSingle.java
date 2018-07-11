package com.ociweb.oe.greenlightning.api;

import com.ociweb.gl.api.ClientHostPortInstance;
import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPRequestService;
import com.ociweb.gl.api.HTTPResponseListener;
import com.ociweb.gl.api.HTTPResponseReader;
import com.ociweb.gl.api.Payloadable;
import com.ociweb.gl.api.PubSubListener;
import com.ociweb.gl.api.PubSubService;
import com.ociweb.gl.api.StartupListener;
import com.ociweb.pronghorn.pipe.ChannelReader;
import com.ociweb.pronghorn.pipe.StructuredReader;
import com.ociweb.pronghorn.util.Appendables;

public class HTTPGetBehaviorSingle implements StartupListener, HTTPResponseListener, PubSubListener {
	
	private ClientHostPortInstance session;
	private final HTTPRequestService clientService;
	private final PubSubService pubSubService;
	 
	public HTTPGetBehaviorSingle(GreenRuntime runtime, ClientHostPortInstance session) {
		this.session = session;
		GreenCommandChannel cmd = runtime.newCommandChannel();
		clientService = cmd.newHTTPClientService();
		pubSubService = cmd.newPubSubService();
		
		
	}


	@Override
	public void startup() {
		pubSubService.publishTopic("next");
	}
	
	long d = 0;
	long c = 0;

	@Override
	public boolean responseHTTP(HTTPResponseReader reader) {
		
		long duration = System.nanoTime()-reqTime;
		
		d+=duration;
		c+=1;
		
		if(0==(0xFFF&c)) {//running average
			Appendables.appendNearestTimeUnit(System.err, d/c, " latency\n");
		}
		
	//	System.out.println(" status:"+reader.statusCode());
	//	System.out.println("   type:"+reader.contentType());
		
		Payloadable payload = new Payloadable() {
			@Override
			public void read(ChannelReader reader) {
				
				StructuredReader s = reader.structured();
				int value1 = s.readInt(Fields.ID1);
				String value2 = s.readText(Fields.ID2);
				
				//System.out.println(value1+"  "+value2);
				
				
				//String readUTFOfLength = reader.readUTFOfLength(reader.available());
				///System.out.println(readUTFOfLength);
			}
		};
		
		reader.openPayloadData( payload );
		
		pubSubService.publishTopic("next");
		
		return true;
	}


	int countDown = 4000;
	long reqTime = 0;

	@Override
	public boolean message(CharSequence topic, ChannelReader payload) {
		
		if (--countDown<=0) {
			clientService.httpGet(session, "/shutdown?key=shutdown");
			pubSubService.publishTopic("shutdown");
		}
		
		reqTime = System.nanoTime();
		return clientService.httpGet(session, "/testPageB");

	}

}
