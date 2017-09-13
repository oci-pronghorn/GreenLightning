package com.ociweb.gl.api;

import com.ociweb.gl.impl.BuilderImpl;
import com.ociweb.gl.impl.schema.MessagePubSub;
import com.ociweb.gl.impl.schema.TrafficOrderSchema;
import com.ociweb.pronghorn.network.schema.ClientHTTPRequestSchema;
import com.ociweb.pronghorn.network.schema.ServerResponseSchema;
import com.ociweb.pronghorn.pipe.PipeConfig;
import com.ociweb.pronghorn.pipe.PipeConfigManager;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;
import com.ociweb.pronghorn.stage.scheduling.NonThreadScheduler;

public class GreenRuntime extends MsgRuntime<BuilderImpl, ListenerFilter>{
	
    public GreenRuntime() {
        this(null);
     }
     
     public GreenRuntime(String[] args) {
         super(args);
      }
     
     public GreenCommandChannel newCommandChannel() { 
    	 return newCommandChannel(0);
     }
     
     public GreenCommandChannel newCommandChannel(int features) { 
         
     	PipeConfigManager pcm = new PipeConfigManager(4, defaultCommandChannelLength, 
     			                                         defaultCommandChannelMaxPayload);

     	pcm.addConfig(defaultCommandChannelLength, defaultCommandChannelHTTPMaxPayload, ClientHTTPRequestSchema.class);
     	pcm.addConfig(defaultCommandChannelLength,0,TrafficOrderSchema.class);
     	
     	return this.builder.newCommandChannel(
 				features,
 				parallelInstanceUnderActiveConstruction,
 				pcm
 		  );    	
     }

//     //must not have two ints or we may use a comma betweeen them to cause a bug.
//     //Delete method in October...     
//     @Deprecated
//     public GreenCommandChannel newCommandChannel(int features, int customChannelLength, CharSequence ... supportedTopics) { 
//        
//     	PipeConfigManager pcm = new PipeConfigManager(4, defaultCommandChannelLength, 
//     			                                         defaultCommandChannelMaxPayload);
//     	
//     	pcm.addConfig(customChannelLength,defaultCommandChannelMaxPayload,MessagePubSub.class);
//     	pcm.addConfig(defaultCommandChannelLength, defaultCommandChannelHTTPMaxPayload, ClientHTTPRequestSchema.class);
//     	pcm.addConfig(customChannelLength,0,TrafficOrderSchema.class);
//     	pcm.addConfig(customChannelLength,defaultCommandChannelHTTPMaxPayload,ServerResponseSchema.class);
//     	//pcm.addConfig(100,0,TrafficAckSchema.class);
//     	
//         return this.builder.newCommandChannel(
// 				features,
// 				parallelInstanceUnderActiveConstruction,
// 				pcm
// 		  );        
//     }
     
    public static GreenRuntime run(GreenApp app) {
    	return run(app,new String[0]);
    }
    
	public static GreenRuntime run(GreenApp app, String[] args) {
		GreenRuntime runtime = new GreenRuntime(args);
 
    	app.declareConfiguration(runtime.getBuilder());
	    GraphManager.addDefaultNota(runtime.gm, GraphManager.SCHEDULE_RATE, runtime.builder.getDefaultSleepRateNS());

	    runtime.declareBehavior(app);
	    
	    System.out.println("To exit app press Ctrl-C");

		runtime.builder.buildStages(runtime.subscriptionPipeLookup, runtime.gm);

	    runtime.logStageScheduleRates();

		if ( runtime.builder.isTelemetryEnabled()) {
			   runtime.telemetryHost = runtime.gm.enableTelemetry(runtime.builder.telemetryHost(),runtime.builder.telmetryPort());
		}
		   //exportGraphDotFile();

		runtime.scheduler = runtime.builder.createScheduler(runtime);
	    runtime.scheduler.startup();

		return runtime;
    }
	
	@Deprecated
    public static GreenRuntime test(GreenApp app) {
    	GreenRuntime runtime = new GreenRuntime();
        test(app, runtime);
		return runtime;
    }

	public static boolean testUntilShutdownRequested(GreenApp app, long timeoutMS) {
		GreenRuntime runtime = new GreenRuntime();
        NonThreadScheduler s = test(app, runtime);
        
        long limit = System.currentTimeMillis() + timeoutMS;
        boolean result = true;
        s.startup();
    	
                
		while (!NonThreadScheduler.isShutdownRequested(s)) {
				s.run();
				if (System.currentTimeMillis()>limit) {
					result = false;
					break;
				}
		}		
		s.shutdown();
		return result;
	}
	
	
	
	private static NonThreadScheduler test(GreenApp app, GreenRuntime runtime) {
		//force hardware to TestHardware regardless of where or what platform its run on.
        //this is done because this is the test() method and must behave the same everywhere.
        runtime.builder = new BuilderImpl(runtime.gm,runtime.args);

    	app.declareConfiguration(runtime.builder);
        GraphManager.addDefaultNota(runtime.gm, GraphManager.SCHEDULE_RATE, runtime.builder.getDefaultSleepRateNS());

        runtime.declareBehavior(app);

		runtime.builder.buildStages(runtime.subscriptionPipeLookup, runtime.gm);

	    runtime.logStageScheduleRates();

	    if ( runtime.builder.isTelemetryEnabled()) {
		   runtime.gm.enableTelemetry(8098);
	    }

	      //exportGraphDotFile();

		runtime.scheduler = new NonThreadScheduler(runtime.gm);
		return (NonThreadScheduler) runtime.scheduler;
	}
    
    
    
    
}
