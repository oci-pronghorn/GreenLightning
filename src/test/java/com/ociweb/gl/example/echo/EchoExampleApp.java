package com.ociweb.gl.example.echo;

import com.ociweb.gl.api.Builder;
import com.ociweb.gl.api.GreenApp;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPResponseService;
import com.ociweb.gl.api.Writable;
import com.ociweb.pronghorn.network.config.HTTPHeaderDefaults;
import com.ociweb.pronghorn.network.http.HeaderWritable;
import com.ociweb.pronghorn.network.http.HeaderWriter;
import com.ociweb.pronghorn.pipe.ChannelWriter;

public class EchoExampleApp implements GreenApp {

	private final Appendable target;
	public EchoExampleApp(Appendable target) {
		this.target = target;
	}
	
	@Override
	public void declareConfiguration(Builder builder) {
		builder.useHTTP1xServer(8084)
	       .useInsecureServer()
	       .echoHeaders(128, HTTPHeaderDefaults.DNT, HTTPHeaderDefaults.STRICT_TRANSPORT_SECURITY)
	       .setHost("127.0.0.1");
		
		builder.defineRoute()
	       .path("/test")
		   .routeId();
	}

	@Override
	public void declareBehavior(GreenRuntime runtime) {
		HTTPResponseService resp = runtime.newCommandChannel().newHTTPResponseService();		
		runtime.addRestListener("restListener",(r)->{	
			
			HeaderWritable headers = new HeaderWritable() {
				@Override
				public void write(HeaderWriter writer) {
					
					writer.write(HTTPHeaderDefaults.DNT, "true");
					writer.write(HTTPHeaderDefaults.STRICT_TRANSPORT_SECURITY, "hello");
					
				}
			};
			Writable writable = new Writable() {
				@Override
				public void write(ChannelWriter writer) {
					//no response
				}
			};
			return resp.publishHTTPResponse(r, headers, writable);
			
		}).includeAllRoutes();
		
	}

}
