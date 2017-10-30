package com.ociweb.oe.greenlightning.api;

import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPResponseListener;
import com.ociweb.gl.api.HTTPResponseReader;
import com.ociweb.gl.api.HTTPSession;
import com.ociweb.gl.api.Payloadable;
import com.ociweb.gl.api.PubSubListener;
import com.ociweb.gl.api.StartupListener;
import com.ociweb.gl.api.TimeListener;
import com.ociweb.pronghorn.pipe.ChannelReader;
import com.ociweb.pronghorn.util.Appendables;

public class HTTPGetBehaviorSingle implements StartupListener, HTTPResponseListener, PubSubListener {

	
	private final GreenCommandChannel cmd;
	private HTTPSession session = new HTTPSession(
			//"javanut.com",80,0);
			"127.0.0.1",8088,0);
	 
	public HTTPGetBehaviorSingle(GreenRuntime runtime) {
		cmd = runtime.newCommandChannel(NET_REQUESTER | DYNAMIC_MESSAGING);
	}


	@Override
	public void startup() {
	    cmd.publishTopic("next");
	}
	
	

	@Override
	public boolean responseHTTP(HTTPResponseReader reader) {
		
		long duration = System.nanoTime()-reqTime;
		Appendables.appendNearestTimeUnit(System.err, duration);
		System.err.println(" latency\n");
		
		System.out.println(" status:"+reader.statusCode());
		System.out.println("   type:"+reader.contentType());
		
		Payloadable payload = new Payloadable() {
			@Override
			public void read(ChannelReader reader) {
				System.out.println(reader.readUTFOfLength(reader.available()));
			}
		};
		
		reader.openPayloadData( payload );
		
		cmd.publishTopic("next");
		
		return true;
	}


	int countDown = 15;
	long reqTime = 0;

	@Override
	public boolean message(CharSequence topic, ChannelReader payload) {
		
		if (--countDown<=0) {
			cmd.httpGet(session, "/shutdown?key=shutdown");
			cmd.publishTopic("shutdown");
		}
		
		reqTime = System.nanoTime();
		return cmd.httpGet(session, "/testPageB");

	}

}
