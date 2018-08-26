package com.ociweb.gl.test;

import com.ociweb.gl.api.GreenRuntime;

public class LoadTester {
	
	public static <T, A extends Appendable> A runClient(WritableFactory testData,
			ValidatorFactory validator, String route, boolean useTLS, boolean telemetry, int parallelTracks,
			int cyclesPerTrack, String host, int port, int timeoutMS, A target) {
		return runClient(testData,validator,route,useTLS,telemetry,parallelTracks,cyclesPerTrack,host,port,timeoutMS,0/*inFlightBits*/,target);
	};
	
	public static <T, A extends Appendable> A runClient(WritableFactory testData,
			ValidatorFactory validator, String route, boolean useTLS, boolean telemetry, int parallelTracks,
			int cyclesPerTrack, String host, int port, int timeoutMS, int inFlightBits,A target) {

		ParallelClientLoadTesterConfig testerConfig = new ParallelClientLoadTesterConfig(parallelTracks, cyclesPerTrack,
				port, route, telemetry);
		testerConfig.insecureClient = !useTLS;
		testerConfig.host = host;
		testerConfig.simultaneousRequestsPerTrackBits = inFlightBits;		
		testerConfig.target = target;
		
		ParallelClientLoadTesterPayload payload = new ParallelClientLoadTesterPayload(); // calling get
		
		payload.post = testData;
		payload.validator = validator;

		GreenRuntime.testConcurrentUntilShutdownRequested(new ParallelClientLoadTester(testerConfig, payload), timeoutMS);

		return target;
	}
	
	public static <T, A extends Appendable> A runClient(WritableFactory testData,
			ValidatorFactory validator, RouteFactory route, boolean useTLS, boolean telemetry, int parallelTracks,
			int cyclesPerTrack, String host, int port, int timeoutMS,  A target) {
		return runClient(testData,validator,route,useTLS,telemetry,parallelTracks,cyclesPerTrack,host,port,timeoutMS,0,target);
	}
	
	public static <T, A extends Appendable> A runClient(WritableFactory testData,
			ValidatorFactory validator, RouteFactory route, boolean useTLS, boolean telemetry, int parallelTracks,
			int cyclesPerTrack, String host, int port, int timeoutMS, int inFlightBits, A target) {

		ParallelClientLoadTesterConfig testerConfig = new ParallelClientLoadTesterConfig(parallelTracks, cyclesPerTrack,
				port, route, telemetry);
		testerConfig.insecureClient = !useTLS;
		testerConfig.host = host;
		testerConfig.target = target;
		testerConfig.simultaneousRequestsPerTrackBits = inFlightBits;
		
		ParallelClientLoadTesterPayload payload = new ParallelClientLoadTesterPayload(); // calling get
		
		payload.post = testData;
		payload.validator = validator;

		GreenRuntime.testConcurrentUntilShutdownRequested(new ParallelClientLoadTester(testerConfig, payload),
				timeoutMS);

		return target;
	}

}
