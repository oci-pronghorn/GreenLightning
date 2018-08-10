package com.ociweb.gl.test;

import java.util.function.Supplier;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPResponseListener;
import com.ociweb.gl.api.Writable;
import com.ociweb.json.encode.JSONRenderer;
import com.ociweb.pronghorn.pipe.ChannelWriter;

public class LoadTester {

	
	public static <T, A extends Appendable> A runClient(JSONRenderer<T> jsonRenderer, 
			  Supplier<T> testObject,
		      HTTPResponseListener validator, 
	          String route, boolean useTLS, boolean telemetry,
	          int parallelTracks, int cyclesPerTrack, 
	          String host, int port,
	          int timeoutMS
			) {
		return (A) runClient(jsonRenderer,testObject,validator,route,useTLS,telemetry, parallelTracks, cyclesPerTrack, host, port, timeoutMS, new StringBuilder());
	}
	
	public static <T, A extends Appendable> A runClient(JSONRenderer<T> jsonRenderer, 
													  Supplier<T> testObject,
												      HTTPResponseListener validator, 
											          String route, boolean useTLS, boolean telemetry,
											          int parallelTracks, int cyclesPerTrack, 
											          String host, int port,
											          int timeoutMS,
											          A captured
													) {
		
		
		ParallelClientLoadTesterConfig testerConfig = new ParallelClientLoadTesterConfig(parallelTracks, cyclesPerTrack, port, route, telemetry);
		testerConfig.insecureClient = !useTLS;
		testerConfig.host = host;
		
		testerConfig.target = captured;
	
		ParallelClientLoadTesterPayload payload = new ParallelClientLoadTesterPayload(); //calling get
	
		payload.post = new Supplier<Writable>() {			    
			@Override
			public Writable get() {
				return new Writable() {
					@Override
					public void write(ChannelWriter writer) {
						jsonRenderer.render(writer, testObject.get());
					}						
				};
			};				
		};
		
		payload.validate = new Supplier<HTTPResponseListener>() {
			@Override
			public HTTPResponseListener get() {
				return validator;
			}
		};
		
		GreenRuntime.testConcurrentUntilShutdownRequested(new ParallelClientLoadTester(testerConfig, payload), timeoutMS);
	
		return captured;
	}
	
	public static <T> StringBuilder runClient(Supplier<Writable> testData,
			HTTPResponseListener validator, String route, boolean useTLS, boolean telemetry, int parallelTracks,
			int cyclesPerTrack, String host, int port, int timeoutMS) {

		ParallelClientLoadTesterConfig testerConfig = new ParallelClientLoadTesterConfig(parallelTracks, cyclesPerTrack,
				port, route, telemetry);
		testerConfig.insecureClient = !useTLS;
		testerConfig.host = host;

		StringBuilder captured = new StringBuilder();
		testerConfig.target = captured;
		
		ParallelClientLoadTesterPayload payload = new ParallelClientLoadTesterPayload(); // calling get

		
		payload.post = testData;

		payload.validate = new Supplier<HTTPResponseListener>() {
			@Override
			public HTTPResponseListener get() {
				return validator;
			}
		};

		GreenRuntime.testConcurrentUntilShutdownRequested(new ParallelClientLoadTester(testerConfig, payload),
				timeoutMS);

		return captured;
	}
	
	public static <T> StringBuilder runClient(Supplier<Writable> testData,
			HTTPResponseListener validator, Supplier<String> route, boolean useTLS, boolean telemetry, int parallelTracks,
			int cyclesPerTrack, String host, int port, int timeoutMS) {

		ParallelClientLoadTesterConfig testerConfig = new ParallelClientLoadTesterConfig(parallelTracks, cyclesPerTrack,
				port, route, telemetry);
		testerConfig.insecureClient = !useTLS;
		testerConfig.host = host;

		StringBuilder captured = new StringBuilder();
		testerConfig.target = captured;
		
		ParallelClientLoadTesterPayload payload = new ParallelClientLoadTesterPayload(); // calling get

		
		payload.post = testData;

		payload.validate = new Supplier<HTTPResponseListener>() {
			@Override
			public HTTPResponseListener get() {
				return validator;
			}
		};

		GreenRuntime.testConcurrentUntilShutdownRequested(new ParallelClientLoadTester(testerConfig, payload),
				timeoutMS);

		return captured;
	}

}
