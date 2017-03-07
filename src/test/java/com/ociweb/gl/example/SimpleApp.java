package com.ociweb.gl.example;

import com.ociweb.gl.api.Builder;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.TimeTrigger;
import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;
import com.ociweb.pronghorn.network.config.HTTPHeaderKey;
import com.ociweb.pronghorn.network.config.HTTPHeaderKeyDefaults;
import com.ociweb.pronghorn.network.schema.ServerResponseSchema;
import com.ociweb.pronghorn.pipe.DataOutputBlobWriter;
import com.ociweb.pronghorn.util.Appendables;
import com.ociweb.gl.api.GreenApp;

public class SimpleApp implements GreenApp {

	public int ADD_ID1;
	public int ADD_ID2;
	public int FILE_ID1;
	
    public static void main( String[] args ) {
        GreenRuntime.run(new SimpleApp());
    }
	
	@Override
	public void declareConfiguration(Builder builder) {
		
		builder.setTriggerRate(TimeTrigger.OnTheSecond);
	//	builder.limitThreads(8); TODO: this works however its spending too much time spinning on the threads.
		builder.parallelism(8);
				
		boolean isTLS = false;
		boolean isLarge = true;
		String bindHost = "127.0.0.1";
		int bindPort = 8081;
		builder.enableServer(isTLS, isLarge, bindHost, bindPort);
		
		ADD_ID1 = builder.registerRoute("/groovyadd/%i%./%i%.");
		ADD_ID2 = builder.registerRoute("/add/%i%./%i%.");//, HTTPHeaderKeyDefaults.CONTENT_TYPE, HTTPHeaderKeyDefaults.UPGRADE);
		FILE_ID1 = builder.registerRoute("/%b");//TODO: if this is first it ignores the rest of the paths, shoud fix bug
		
		
		//ADD_ID1 = builder.registerRoute("/groovyadd/%i/%i");
		//ADD_ID2 = builder.registerRoute("/add/%i/%i");//, HTTPHeaderKeyDefaults.CONTENT_TYPE, HTTPHeaderKeyDefaults.UPGRADE);
		
		//TODO: must add support for Unroutable choices
//		FILE_ID1 = builder.registerRoute("/favicon.ico"); //Just eat the reequest??
		
       //TODO: this breaks when we add headers....
	}

	@Override
	public void declareBehavior(GreenRuntime runtime) {		
		
		
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
		
		runtime.addRestListener(new MathUnit(runtime, ADD_ID1, ADD_ID2)); //accept all registered routes
		
		
		
	}
	
}
