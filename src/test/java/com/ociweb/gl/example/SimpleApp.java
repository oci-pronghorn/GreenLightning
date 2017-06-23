package com.ociweb.gl.example;

import com.ociweb.gl.api.Builder;
import com.ociweb.gl.api.GreenApp;
import com.ociweb.gl.api.MsgRuntime;
import com.ociweb.gl.api.TimeTrigger;
import com.ociweb.pronghorn.network.config.HTTPHeaderDefaults;

public class SimpleApp implements GreenApp {

	public int ADD_ID1;
	public int ADD_ID2;
	public int FILE_ID1;
	public int SIMPLE_ADD_ID1;
	
	private final int port;
	private final boolean isLarge;
	private final boolean isTLS;
	private MathUnit singleInstance;	
	
	public SimpleApp(int port, boolean isLarge, boolean isTLS) {
		this.port = port;
		this.isLarge = isLarge;
		this.isTLS = isTLS;
	}
	
    public static void main( String[] args ) {
        MsgRuntime.run(new SimpleApp(8081,true,false));
    }
	
    
	@Override
	public void declareConfiguration(Builder builder) {

		builder.setTriggerRate(TimeTrigger.OnTheSecond);
		builder.parallelism(8);
				
		String bindHost = "127.0.0.1";
		builder.enableServer(isTLS, isLarge, bindHost, port);
		
		ADD_ID2 = builder.registerRoute("/add/^{a}/^{b}");//, HTTPHeaderKeyDefaults.CONTENT_TYPE, HTTPHeaderKeyDefaults.UPGRADE);
		ADD_ID1 = builder.registerRoute("/groovyadd/^{a}/^{b}",HTTPHeaderDefaults.COOKIE.rootBytes());
		
		FILE_ID1 = builder.registerRoute("/${unknown}");//TODO: if this is first it ignores the rest of the paths, TODO: should fix bug
		
		SIMPLE_ADD_ID1 = builder.registerRoute("/simpleadd/#{a}/#{b}",HTTPHeaderDefaults.COOKIE.rootBytes());

	}
	
	@Override
	public void declareBehavior(MsgRuntime runtime) {		
				
		runtime.addRestListener(new MathUnitSimple((MsgRuntime) runtime), SIMPLE_ADD_ID1);
		runtime.addRestListener(singleInstance = new MathUnit((MsgRuntime) runtime), ADD_ID1, ADD_ID2); //accept all registered routes
	}

	
	public String getLastCookie() {
		return singleInstance.getLastCookie();
	}
	
}
