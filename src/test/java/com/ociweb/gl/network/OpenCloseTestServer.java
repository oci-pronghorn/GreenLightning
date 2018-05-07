package com.ociweb.gl.network;

import com.ociweb.gl.api.Builder;
import com.ociweb.gl.api.GreenApp;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPResponseService;
import com.ociweb.gl.api.HeaderWritable;
import com.ociweb.gl.api.Writable;
import com.ociweb.pronghorn.network.config.HTTPHeaderDefaults;

public class OpenCloseTestServer implements GreenApp {

	private final Appendable target;
	private final int port;
	private final boolean telemetry;
	private int neverCloseRoute;
	private int alwaysCloseRoute;
	
	public OpenCloseTestServer(int port, boolean telemetry, Appendable target) {
		this.target = target;
		this.port = port;
		this.telemetry = telemetry;
	}

	@Override
	public void declareConfiguration(Builder builder) {
		
		if (telemetry) {
			builder.enableTelemetry(8076);
		}
		
		builder.useHTTP1xServer(port)
			   .setHost("127.0.0.1")
			   .setMaxConnectionBits(3)//only 8 connections
			   .setDecryptionUnitsPerTrack(12)
			   .setEncryptionUnitsPerTrack(12)
		       .useInsecureServer();
		
		neverCloseRoute = builder.defineRoute().path("neverclose").routeId();
		alwaysCloseRoute = builder.defineRoute().path("alwaysclose").routeId();
				       
	}

	@Override
	public void declareBehavior(GreenRuntime runtime) {

		HTTPResponseService respClose = runtime.newCommandChannel().newHTTPResponseService();

		runtime.addRestListener((r) -> {
			
			HeaderWritable headers = (w)->{
				w.write(HTTPHeaderDefaults.CONNECTION, "close");
			};
			
			return respClose.publishHTTPResponse(r, headers, Writable.NO_OP);

		}).includeRoutes(alwaysCloseRoute);

		HTTPResponseService respOpen = runtime.newCommandChannel().newHTTPResponseService();

		runtime.addRestListener((r) -> {
			respOpen.publishHTTPResponse(r, 200);
			return true;
		}).includeRoutes(neverCloseRoute);
	}

}
