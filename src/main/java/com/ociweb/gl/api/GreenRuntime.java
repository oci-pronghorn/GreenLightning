package com.ociweb.gl.api;

import com.ociweb.gl.impl.BuilderImpl;
import com.ociweb.gl.impl.schema.MessagePubSub;
import com.ociweb.gl.impl.schema.TrafficOrderSchema;
import com.ociweb.pronghorn.network.schema.ServerResponseSchema;
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
     
     public GreenCommandChannel newCommandChannel(int features) { 
         
     	PipeConfigManager pcm = new PipeConfigManager(4, defaultCommandChannelLength, defaultCommandChannelMaxPayload);

     	pcm.addConfig(requestNetConfig);
     	pcm.addConfig(defaultCommandChannelLength,0,TrafficOrderSchema.class);
     	pcm.addConfig(serverResponseNetConfig);
     	    	
     	return this.builder.newCommandChannel(
 				features,
 				parallelInstanceUnderActiveConstruction,
 				pcm
 		  );    	
     }

     public GreenCommandChannel newCommandChannel(int features, int customChannelLength, CharSequence ... supportedTopics) { 
        
     	PipeConfigManager pcm = new PipeConfigManager(4, defaultCommandChannelLength, defaultCommandChannelMaxPayload);
     	
     	pcm.addConfig(customChannelLength,defaultCommandChannelMaxPayload,MessagePubSub.class);
     	pcm.addConfig(requestNetConfig);
     	pcm.addConfig(customChannelLength,0,TrafficOrderSchema.class);
     	pcm.addConfig(customChannelLength,defaultCommandChannelHTTPResponseMaxPayload,ServerResponseSchema.class);
     	   	
         return this.builder.newCommandChannel(
 				features,
 				parallelInstanceUnderActiveConstruction,
 				pcm
 		  );        
     }
     
    public static GreenRuntime run(GreenApp app) {
    	return run(app,null);
    }
    
	public static GreenRuntime run(GreenApp app, String[] args) {
		GreenRuntime runtime = new GreenRuntime(args);
        try {
        	app.declareConfiguration(runtime.getBuilder());
		    GraphManager.addDefaultNota(runtime.gm, GraphManager.SCHEDULE_RATE, runtime.builder.getDefaultSleepRateNS());

		    runtime.declareBehavior(app);

		    //TODO: at this point realize the stages in declare behavior
		    //      all updates are done so create the reactors with the right pipes and names
		    //      this change will let us move routes to part of the fluent API plus other benifits..
		    //      move all reactor fields into object created early, shell is created here.
		    //      register must hold list of all temp objects (linked list to preserve order?)
		    
		    System.out.println("To exit app press Ctrl-C");

				runtime.builder.buildStages(runtime.subscriptionPipeLookup, runtime.netPipeLookup, runtime.gm);

				   runtime.logStageScheduleRates();

				   if ( runtime.builder.isTelemetryEnabled()) {
					   runtime.gm.enableTelemetry(8098);
				   }
			   //exportGraphDotFile();

			runtime.scheduler = runtime.builder.createScheduler(runtime);
		    runtime.scheduler.startup();
		} catch (Throwable t) {
		    t.printStackTrace();
		    System.exit(-1);
		}
		return runtime;
    }
	
    public static GreenRuntime test(GreenApp app) {
    	GreenRuntime runtime = new GreenRuntime();
        //force hardware to TestHardware regardless of where or what platform its run on.
        //this is done because this is the test() method and must behave the same everywhere.
        runtime.builder = new BuilderImpl(runtime.gm);

        try {
        	app.declareConfiguration(runtime.builder);
            GraphManager.addDefaultNota(runtime.gm, GraphManager.SCHEDULE_RATE, runtime.builder.getDefaultSleepRateNS());

            runtime.declareBehavior(app);

				runtime.builder.buildStages(runtime.subscriptionPipeLookup, runtime.netPipeLookup, runtime.gm);

				   runtime.logStageScheduleRates();

				   if ( runtime.builder.isTelemetryEnabled()) {
					   runtime.gm.enableTelemetry(8098);
				   }
			   //exportGraphDotFile();

			   runtime.scheduler = new NonThreadScheduler(runtime.gm);
					   //runtime.builder.createScheduler(runtime);
            //for test we do not call startup and wait instead for this to be done by test.
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(-1);
        }
        return runtime;
    }
    
}
