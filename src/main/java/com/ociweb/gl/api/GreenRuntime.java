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

      	PipeConfigManager pcm = new PipeConfigManager(4, defaultCommandChannelLength, 
      			                                         defaultCommandChannelMaxPayload);

      	pcm.addConfig(defaultCommandChannelLength, defaultCommandChannelHTTPMaxPayload, ClientHTTPRequestSchema.class);
      	pcm.addConfig(defaultCommandChannelLength,0,TrafficOrderSchema.class);
      	
      	return this.builder.newCommandChannel(  				
  				parallelInstanceUnderActiveConstruction,
  				pcm
  		  );    
     }
     
//     @Deprecated
//     private GreenCommandChannel newCommandChannel(int features) { 
//         
//     	PipeConfigManager pcm = new PipeConfigManager(4, defaultCommandChannelLength, 
//     			                                         defaultCommandChannelMaxPayload);
//
//     	pcm.addConfig(defaultCommandChannelLength, defaultCommandChannelHTTPMaxPayload, ClientHTTPRequestSchema.class);
//     	pcm.addConfig(defaultCommandChannelLength,0,TrafficOrderSchema.class);
//     	
//     	return this.builder.newCommandChannel(
// 				features,
// 				parallelInstanceUnderActiveConstruction,
// 				pcm
// 		  );    	
//     }
     
    public static GreenRuntime run(GreenApp app) {
    	return run(app,new String[0]);
    }

	/**
	 *
	 * @param app GreenApp arg used in runtime.declareBehavior
	 * @param args String array arg
	 * @return runtime
	 */
	public static GreenRuntime run(GreenApp app, String[] args) {
		GreenRuntime runtime = new GreenRuntime(args, app.getClass().getSimpleName());
		app.declareConfiguration(runtime.getBuilder());

	    GraphManager.addDefaultNota(runtime.gm, GraphManager.SCHEDULE_RATE, runtime.builder.getDefaultSleepRateNS());

	    runtime.declareBehavior(app);

		runtime.builder.buildStages(runtime);
		
	    //runtime.logStageScheduleRates();

		TelemetryConfig telemetryConfig = runtime.builder.getTelemetryConfig();
		if (telemetryConfig != null) {
			runtime.telemetryHost = runtime.gm.enableTelemetry(telemetryConfig.getHost(), telemetryConfig.getPort());
		}
		   //exportGraphDotFile();

		//runtime.scheduler = StageScheduler.threadPerStage(runtime.gm);//hack test.				
		runtime.setScheduler(runtime.builder.createScheduler(runtime));
	    

		System.out.println("To exit app press Ctrl-C");
		
		System.gc();
		runtime.getScheduler().startup();

		return runtime;
    }
	
	public void checkForException() {
		getScheduler().checkForException();
	}

	/**
	 *
	 * @param app GreenApp arg used in MsgRuntime
	 * @param timeoutMS long arg used to set timeout in milliseconds
	 * @return if !runtime.isShutdownComplete() return false else return true
	 */
	public static boolean testConcurrentUntilShutdownRequested(GreenApp app, long timeoutMS) {
		
		 long limit = System.nanoTime() + (timeoutMS*1_000_000L);
		 
		 MsgRuntime runtime = run(app);

    	 while (!runtime.isShutdownComplete()) {
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

	/**
	 *
	 * @param app GreenApp arg used in ScriptedNonThreadScheduler
	 * @param timeoutMS long arg used to set the timeout in milliseconds
	 * @return result (true or false)
	 */
	public static boolean testUntilShutdownRequested(GreenApp app, long timeoutMS) {
		GreenRuntime runtime = new GreenRuntime(app.getClass().getSimpleName());
		
		ScriptedNonThreadScheduler s = test(app, runtime);
 
        long limit = System.nanoTime() + (timeoutMS*1_000_000L);
        boolean result = true;
        boolean hangDectection = false;
		s.startup(hangDectection);
    	                
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

	/**
	 *
	 * @param app GreenApp arg used in runtime.declareBehavior and used to declareConfiguration
	 * @param runtime GreenRuntime arg used with builder.BuilderImpl, builder.setDefaultRate, builder.buildStages and logStageScheduleRates
	 * @return ScriptedNonThreadScheduler runtime.getScheduler()
	 */
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
