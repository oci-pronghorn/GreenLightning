package com.ociweb.gl.example;

import com.ociweb.gl.api.*;
import com.ociweb.pronghorn.network.config.HTTPHeaderDefaults;

public class SimpleApp implements GreenApp {

	public int ADD_ID1;
	public int ADD_ID2;
	public int FILE_ID1;
	public int SIMPLE_ADD_ID1;
	
	private final int port;

	private final boolean isTLS;
	private MathUnit singleInstance;	
	
	public SimpleApp(int port, boolean isTLS) {
		this.port = port;
		this.isTLS = isTLS;
	}
	
    public static void main( String[] args ) {
    	GreenRuntime.run(new SimpleApp(8081,false));
    }
	
    
	@Override
	public void declareConfiguration(Builder builder) {

		builder.setTimerPulseRate(TimeTrigger.OnTheSecond);
					
		String bindHost = "127.0.0.1";
		HTTPServerConfig httpServerConfig = builder.useHTTP1xServer(port).setHost(bindHost);
		if (!isTLS) httpServerConfig.useInsecureServer();
	
		ADD_ID2 = builder.registerRoute("/add/^{a}/^{b}");//, HTTPHeaderKeyDefaults.CONTENT_TYPE, HTTPHeaderKeyDefaults.UPGRADE);
		ADD_ID1 = builder.registerRoute("/groovyadd/^{a}/^{b}",HTTPHeaderDefaults.COOKIE.rootBytes());
		
		FILE_ID1 = builder.registerRoute("/${unknown}");//TODO: if this is first it ignores the rest of the paths, TODO: should fix bug
		
		SIMPLE_ADD_ID1 = builder.registerRoute("/simpleadd/#{a}/#{b}",HTTPHeaderDefaults.COOKIE.rootBytes());

	}
	
	@Override
	public void declareBehavior(GreenRuntime runtime) {		
				
		runtime.addRestListener(new MathUnitSimple(runtime)).includeRoutes(SIMPLE_ADD_ID1);
		runtime.addRestListener(singleInstance = new MathUnit(runtime)).includeRoutes(ADD_ID1, ADD_ID2); //accept all registered routes
	}

	
	public String getLastCookie() {
		return singleInstance.getLastCookie();
	}
	
}
