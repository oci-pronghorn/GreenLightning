package com.ociweb.gl.api;

import static com.ociweb.gl.api.Builder.defaultTelemetryPort;

import com.ociweb.gl.impl.BuilderImpl;
import com.ociweb.gl.impl.schema.TrafficOrderSchema;
import com.ociweb.pronghorn.network.schema.ClientHTTPRequestSchema;
import com.ociweb.pronghorn.pipe.PipeConfigManager;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;
import com.ociweb.pronghorn.stage.scheduling.ScriptedNonThreadScheduler;
import com.ociweb.pronghorn.util.math.ScriptedSchedule;

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

		runtime.builder.buildStages(runtime);

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
		GreenRuntime runtime = new GreenRuntime();
		
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

	    if ( runtime.builder.isTelemetryEnabled()) {
		   runtime.gm.enableTelemetry(defaultTelemetryPort);
	    }

	      //exportGraphDotFile();
	    boolean reverseOrder = false;
		runtime.scheduler = new ScriptedNonThreadScheduler(runtime.gm, reverseOrder);
		
		/////////////
		//new work investigating the skipping of calls
		/////////////
		
		ScriptedNonThreadScheduler s = (ScriptedNonThreadScheduler)runtime.scheduler;
		ScriptedSchedule schedule = s.schedule();
		
//		//[0, 1, 2, 3, 5, 6, 7, -1, 0, 1, 2, 4, 5, 6, 7, -1]
//		System.err.println("testing list");
//		System.err.println(Arrays.toString(schedule.script));
//		
//		for(int i = 0; i<s.stages.length; i++) {
//			System.err.println(i+"  "+s.stages[i].getClass());
//			
//		}
		
		
		//skip script is same length as the script
		GraphManager gm = runtime.gm;
		
		//TODO: must store the run count to the next skip point
		//      skip points are Producers or merge pipe points.
		//      all others are zero, to take point
		//high bit will be used to enable and 0 high for disable
		int[] skipScript = ScriptedNonThreadScheduler.buildSkipScript(schedule, gm, s.stages, schedule.script);
		
//		System.err.println(Arrays.toString(skipScript));
		
		//if enabled
		//if value is > 1
		//if input pipes are empty
		//     jump n
		//else
		//     count down value for each set of empty pipes
		//     if we have zero at next >1 point enable else disable
		
//		
//		for(int i = 0;i<schedule.script.length;i++) {
//			if (schedule.script[i] == -1) {
//				System.err.println("END");
//			} else {
//				System.err.println(s.stages[schedule.script[i]].getClass());
//		
//				int inC = GraphManager.getInputPipeCount(runtime.gm, s.stages[schedule.script[i]].stageId);
//				System.err.print("    input: ");
//				for(int j = 1; j<=inC; j++) {
//					Pipe p = GraphManager.getInputPipe(runtime.gm, s.stages[schedule.script[i]].stageId, j);
//					System.err.print(" "+p.id);					
//				}
//				System.err.println();
//				
//				System.err.print("    output: ");
//				int outC = GraphManager.getOutputPipeCount(runtime.gm, s.stages[schedule.script[i]].stageId);
//				for(int j = 1; j<=outC; j++) {
//					Pipe p = GraphManager.getOutputPipe(runtime.gm, s.stages[schedule.script[i]].stageId, j);
//					System.err.print(" "+p.id);					
//				}
//				System.err.println();
//			}		
//		}
		
		
		////////////////
		////////////////
		/////////////
		
		
		
		return (ScriptedNonThreadScheduler) runtime.scheduler;
	}

    
    
    
    
}
