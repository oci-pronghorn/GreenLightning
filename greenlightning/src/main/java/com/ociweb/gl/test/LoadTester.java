package com.ociweb.gl.test;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;

public class LoadTester {
	
	public static <T, A extends Appendable> A runClient(WritableFactory testData,
			ValidatorFactory validator, String route, boolean useTLS, boolean telemetry, int parallelTracks,
			int cyclesPerTrack, String host, int port, int timeoutMS, A target) {
		return runClient(testData,validator,route,useTLS,telemetry,parallelTracks,cyclesPerTrack,host,port,timeoutMS,0/*inFlightBits*/,target);
	};
	
	public static <T, A extends Appendable> A runClient(WritableFactory testData,
			ValidatorFactory validator, String route, boolean useTLS, boolean telemetry, int parallelTracks,
			int cyclesPerTrack, String host, int port, int timeoutMS, int inFlightBits,A target) {
		return runClient(testData,validator,route,useTLS,telemetry,parallelTracks,cyclesPerTrack,host,port,timeoutMS,inFlightBits,null,target);
	}
	
	//one response actor should only try to manage this many connections unless we are out of cores then we just distribute the load.
	private static final int LIMITED_CONNECTIONS_PER_ACTOR = 64;
	//HIGHVOLUME
	
	public static <T, A extends Appendable> A runClient(WritableFactory testData,
			ValidatorFactory validator, String route, boolean useTLS, boolean telemetry, int concurrentConnections,
			int cyclesPerTrack, String host, int port, int timeoutMS, int inFlightBits, GraphManager graphUnderTest, A target) {
		

		int tracks = Math.min(1+(concurrentConnections/(LIMITED_CONNECTIONS_PER_ACTOR+1)), Runtime.getRuntime().availableProcessors()*2);		
			
		ParallelClientLoadTesterConfig testerConfig = new ParallelClientLoadTesterConfig(tracks, cyclesPerTrack,
																							port, route, telemetry);
		testerConfig.insecureClient = !useTLS;
		testerConfig.host = host;
		testerConfig.telemetryHost = "*.*.*.*";
		testerConfig.simultaneousRequestsPerTrackBits = inFlightBits;		
		testerConfig.target = target;
		testerConfig.graphUnderTest = graphUnderTest;
		
		//must be floor because the tracks may not divide evenly, we do not support more  pipes than we have sessions.
		testerConfig.sessionsPerTrack = (int)Math.floor(concurrentConnections/(float)tracks);
		
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
