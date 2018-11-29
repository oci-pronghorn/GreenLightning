package com.ociweb.gl.api;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.ociweb.gl.impl.BuilderImpl;
import com.ociweb.gl.impl.GreenFrameworkImpl;
import com.ociweb.gl.impl.schema.TrafficOrderSchema;
import com.ociweb.pronghorn.pipe.PipeConfigManager;
import com.ociweb.pronghorn.stage.PronghornStage;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;
import com.ociweb.pronghorn.stage.scheduling.ScriptedNonThreadScheduler;
import com.ociweb.pronghorn.stage.scheduling.StageVisitor;

public class GreenRuntime extends MsgRuntime<BuilderImpl<GreenRuntime>, ListenerFilter, GreenRuntime>{
	
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

    	PipeConfigManager pcm = new PipeConfigManager(4, defaultCommandChannelLength, defaultCommandChannelMaxPayload);

      	pcm.addConfig(defaultCommandChannelLength, 0,TrafficOrderSchema.class);
      	
      	return (GreenCommandChannel) this.builder.newCommandChannel( parallelInstanceUnderActiveConstruction, pcm );    
     }
     

     
    public static GreenRuntime run(GreenApp app) {
    	return run(app,new String[0]);
    }

    
    public static GreenRuntime run(GreenApp app, String[] args) {
    	return run(app, 
    			   args,
    			   new Runnable() {
					@Override
					public void run() {
					}
    			   }, 
    			   new Runnable() {
					@Override
					public void run() {
					}
			       });
    }
    
	public static GreenRuntime run(GreenApp app, 
			                       String[] args,
			                       Runnable cleanShutdown,
			                       Runnable dirtyShutdown
								) {
		GreenRuntime runtime = new GreenRuntime(args, app.getClass().getSimpleName());
		app.declareConfiguration((GreenFramework)runtime.getBuilder());

	    GraphManager.addDefaultNota(runtime.gm, GraphManager.SCHEDULE_RATE, runtime.builder.getDefaultSleepRateNS());

	    runtime.declareBehavior(app);

		runtime.builder.buildStages(runtime);
		
	    //runtime.logStageScheduleRates();

		TelemetryConfig telemetryConfig = runtime.builder.getTelemetryConfig();
		if (telemetryConfig != null) {
			//telemetryHost is not null only when we are running the HTTP telemetry server
			runtime.telemetryHost = runtime.gm.enableTelemetry(telemetryConfig.getHost(), telemetryConfig.getPort());
		}				

		runtime.setScheduler(runtime.builder.createScheduler(runtime,cleanShutdown,dirtyShutdown));
	    
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
	 * @param app GreenApp used to test
	 * @param timeoutMS long arg used to set timeout in milliseconds
	 * @return if !runtime.isShutdownComplete() return false else return true
	 */
	public static boolean testConcurrentUntilShutdownRequested(GreenApp app, long timeoutMS) {
		
		 final CyclicBarrier barrier = new CyclicBarrier(2);
		 
		 Runnable cleanShutdown = new Runnable() {
			@Override
			public void run() {
				try {
					barrier.await();
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				} catch (BrokenBarrierException e) {
					throw new RuntimeException(e);
				}    
			}
	    };
		   
		   
		Runnable dirtyShutdown = new Runnable() {
			@Override
			public void run() {
				try {
					barrier.await();
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				} catch (BrokenBarrierException e) {
					throw new RuntimeException(e);
				}    
			}
		   };
		   
		MsgRuntime runtime = run(app, new String[0], cleanShutdown, dirtyShutdown);

		try {			
			//timeout code is now broken by this..
			barrier.await(timeoutMS,TimeUnit.MILLISECONDS);
		} catch (TimeoutException e) {
			System.err.println("exit due to timeout");
			return false;
		} catch (InterruptedException e) {
			logger.info("exit",e);
			return false;
		} catch (BrokenBarrierException e) {
			logger.info("exit",e);
			return false;
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
        runtime.builder = new GreenFrameworkImpl(runtime.gm,runtime.args);

        //lowered for tests, we want tests to run faster, tests probably run on bigger systems.
        runtime.builder.setDefaultRate(10_000);
        
    	app.declareConfiguration((GreenFramework)runtime.builder);
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
