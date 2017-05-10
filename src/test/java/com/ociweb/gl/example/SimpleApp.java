package com.ociweb.gl.example;

import com.ociweb.gl.api.Builder;
import com.ociweb.gl.api.GreenApp;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.TimeTrigger;
import com.ociweb.pronghorn.network.config.HTTPHeaderDefaults;

public class SimpleApp implements GreenApp {

	public int ADD_ID1;
	public int ADD_ID2;
	public int FILE_ID1;
	
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
        GreenRuntime.run(new SimpleApp(8081,true,false));
    }
	
    
	@Override
	public void declareConfiguration(Builder builder) {
		
		builder.setTriggerRate(TimeTrigger.OnTheSecond);
	//	builder.limitThreads(8); TODO: this works however its spending too much time spinning on the threads.
		builder.parallelism(8);
				
		String bindHost = "127.0.0.1";
		builder.enableServer(isTLS, isLarge, bindHost, port);
		
		ADD_ID1 = builder.registerRoute("/groovyadd/^{a}/^{b}",HTTPHeaderDefaults.COOKIE.rootBytes());
		ADD_ID2 = builder.registerRoute("/add/^a/^b");//, HTTPHeaderKeyDefaults.CONTENT_TYPE, HTTPHeaderKeyDefaults.UPGRADE);
		
		FILE_ID1 = builder.registerRoute("/${unknown}");//TODO: if this is first it ignores the rest of the paths, TODO: should fix bug
		
		//int fieldId = builder.fieldId(ADD_ID2, "name".getBytes());
		
		
		//ADD_ID1 = builder.registerRoute("/groovyadd/%i/%i");
		//ADD_ID2 = builder.registerRoute("/add/%i/%i");//, HTTPHeaderKeyDefaults.CONTENT_TYPE, HTTPHeaderKeyDefaults.UPGRADE);
		
		//TODO: must add support for Unroutable choices
//		FILE_ID1 = builder.registerRoute("/favicon.ico"); //Just eat the reequest??
		
       //TODO: this breaks when we add headers....
	}

	
	
	@Override
	public void declareBehavior(GreenRuntime runtime) {		
		
		
		runtime.addRestListener(singleInstance = new MathUnit(runtime), ADD_ID1, ADD_ID2); //accept all registered routes
		
		
//		runtime.addTimeListener((now)->{Appendables.appendValue(System.out, "This is run every second on the second: ",now,"ms\n");
//		                               });
//		
//		runtime.addStartupListener(()->{System.out.println("this is run first");  });
	
		///TODO: this gets skipped as an older response?
		
//		runtime.addFileServer("/home/nate/git/GreenLightning/src/main/resources/site/index.html", FILE_ID1); 

		//runtime.addFileServer("/home/nate/git/GreenLightning/src/main/resources/site/index.html", FILE_ID1);    
  
		
		
		//runtime.addRestListener(new MathUnit(runtime, ADD_ID1, ADD_ID2)); //accept all registered routes
		//runtime.addRestListener(new MathUnit(runtime, ADD_ID2));//, ADD_ID2)); //accept all registered routes
		
		
		
	}

	@Override
	public void declareParallelBehavior(GreenRuntime runtime) {	
		
	//	runtime.addRestListener(new MathUnit(runtime, ADD_ID1));//, ADD_ID2)); //accept all registered routes

		runtime.addFileServer("/home/nate/git/GreenLightning/src/main/resources/site/index.html", FILE_ID1); 
//		runtime.addFileServer("/home/nate/git/GreenLightning/src/main/resources/site/index.html", FILE_ID1);  
		
		//TODO wrong matching IDS plus no server startup??
		
		//runtime.addRestListener(new MathUnit(runtime), ADD_ID1, ADD_ID2); //accept all registered routes
		
	}
	
	public String getLastCookie() {
		return singleInstance.getLastCookie();
	}
	
}
