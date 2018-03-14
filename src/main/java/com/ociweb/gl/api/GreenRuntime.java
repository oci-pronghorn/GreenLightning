package com.ociweb.gl.api;

import com.ociweb.gl.impl.BuilderImpl;
import com.ociweb.gl.impl.schema.TrafficOrderSchema;
import com.ociweb.pronghorn.network.schema.ClientHTTPRequestSchema;
import com.ociweb.pronghorn.pipe.PipeConfigManager;
import com.ociweb.pronghorn.stage.PronghornStage;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;
import com.ociweb.pronghorn.stage.scheduling.ScriptedNonThreadScheduler;
import com.ociweb.pronghorn.stage.scheduling.StageVisitor;

public class GreenRuntime extends MsgRuntime<BuilderImpl, ListenerFilter>{
	
     public GreenRuntime() {
        this(new String[0],null);
     }
     
     public GreenRuntime(String name) {
        this(new String[0],name);
     }
     
     public GreenRuntime(String[] args) {
         super(args,null);
     }
     
     public GreenRuntime(String[] args, String name) {
         super(args,name);
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
     
    public static GreenRuntime run(GreenApp app) {
    	return run(app,new String[0]);
    }
    
	public static GreenRuntime run(GreenApp app, String[] args) {
		GreenRuntime runtime = new GreenRuntime(args, app.getClass().getSimpleName());
 
    	app.declareConfiguration(runtime.getBuilder());
	    GraphManager.addDefaultNota(runtime.gm, GraphManager.SCHEDULE_RATE, runtime.builder.getDefaultSleepRateNS());

	    runtime.declareBehavior(app);
	    
	    System.out.println("To exit app press Ctrl-C");

		runtime.builder.buildStages(runtime);

	    runtime.logStageScheduleRates();

		TelemetryConfig telemetryConfig = runtime.builder.getTelemetryConfig();
		if (telemetryConfig != null) {
			runtime.telemetryHost = runtime.gm.enableTelemetry(telemetryConfig.getHost(), telemetryConfig.getPort());
		}
		   //exportGraphDotFile();

		//runtime.scheduler = StageScheduler.threadPerStage(runtime.gm);//hack test.				
		runtime.setScheduler(runtime.builder.createScheduler(runtime));
	    
		runtime.getScheduler().startup();

		return runtime;
    }
	
	public void checkForException() {
		getScheduler().checkForException();
	}
	
	@Deprecated
    public static GreenRuntime test(GreenApp app) {
    	GreenRuntime runtime = new GreenRuntime();
        test(app, runtime);
		return runtime;
    }
	
	public static boolean testConcurrentUntilShutdownRequested(GreenApp app, long timeoutMS) {
		
		 long limit = System.nanoTime() + (timeoutMS*1_000_000L);
		 
		 MsgRuntime runtime = run(app);

    	 while (!runtime.isShutdownRequested()) {
    		if (System.nanoTime() > limit) {
				System.err.println("exit due to timeout");
				return false;
    		}
    		try {
				Thread.sleep(2);
			} catch (InterruptedException e) {
				return false;
			}
    	 }
    	 return true;
	}

	public static boolean testUntilShutdownRequested(GreenApp app, long timeoutMS) {
		GreenRuntime runtime = new GreenRuntime(app.getClass().getSimpleName());
		
		ScriptedNonThreadScheduler s = test(app, runtime);
 
        long limit = System.nanoTime() + (timeoutMS*1_000_000L);
        boolean result = true;
        s.startup();
    	                
		while (!ScriptedNonThreadScheduler.isShutdownRequested(s)) {

				s.run();
				if (System.nanoTime() > limit) {
					System.err.println("exit due to timeout");
					result = false;
					break;
				}
		}		
		
		return result;
	}
	
	
	
	private static ScriptedNonThreadScheduler test(GreenApp app, GreenRuntime runtime) {
		//force hardware to TestHardware regardless of where or what platform its run on.
        //this is done because this is the test() method and must behave the same everywhere.
        runtime.builder = new BuilderImpl(runtime.gm,runtime.args);

        //lowered for tests, we want tests to run faster, tests probably run on bigger systems.
        runtime.builder.setDefaultRate(10_000);
        
    	app.declareConfiguration(runtime.builder);
        GraphManager.addDefaultNota(runtime.gm, GraphManager.SCHEDULE_RATE, runtime.builder.getDefaultSleepRateNS());

        runtime.declareBehavior(app);

		runtime.builder.buildStages(runtime);

	    runtime.logStageScheduleRates();

		TelemetryConfig telemetryConfig = runtime.builder.getTelemetryConfig();
		if (telemetryConfig != null) {
		   runtime.gm.enableTelemetry(telemetryConfig.getPort());
	    }

	      //exportGraphDotFile();
	    boolean reverseOrder = false;
		StageVisitor badPlayers = new StageVisitor(){
			
			byte[] seen = new byte[GraphManager.countStages(runtime.gm)+1];
				
			@Override
			public void visit(PronghornStage stage) {
				if (0==seen[stage.stageId]) {
					seen[stage.stageId] = 1;
					logger.warn("Slow or blocking stage detected, investigation required: {}",stage);
				}
			}
			
		};
		runtime.setScheduler(new ScriptedNonThreadScheduler(runtime.gm, reverseOrder, badPlayers,null));
		
		return (ScriptedNonThreadScheduler) runtime.getScheduler();
	}

    
    
    
    
}
