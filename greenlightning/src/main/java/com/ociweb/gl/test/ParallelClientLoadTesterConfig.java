package com.ociweb.gl.test;

import java.util.concurrent.atomic.AtomicInteger;

import com.ociweb.gl.api.ArgumentProvider;
import com.ociweb.gl.api.TelemetryConfig;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;

public class ParallelClientLoadTesterConfig {
    public String host = "127.0.0.1";
    public int port = 8080;
    
    public RouteFactory route;
    
    public boolean insecureClient = true;
    public int parallelTracks = 4;
    public int cyclesPerTrack = 1;
    public long durationNanos = 0;
 
    public Integer telemetryPort = null;
    public String telemetryHost = null;
    public int warmup = 0;
    public Long cycleRate = 4_000L; //very fast rate
	public int simultaneousRequestsPerTrackBits = 0; // as power of 2. 0 == serial requests on a track
	public Appendable target = System.out;
	public GraphManager graphUnderTest;
	public int sessionsPerTrack = 1;//default of 1 connection per track
	
    public ParallelClientLoadTesterConfig() {
    }

    public ParallelClientLoadTesterConfig(ArgumentProvider args) {
        inject(args);
    }

    public void inject(ArgumentProvider args) {
        host = args.getArgumentValue("--host", "-h", host);
        port = args.getArgumentValue("--port", "-p", port);
        String routeIn = args.getArgumentValue("--route", "-r", "");
        route = new RouteFactory() {
			@Override
			public String route(long callInstance) {
				return routeIn;
			}
        };
        
        insecureClient = args.getArgumentValue("--insecure", "-is", insecureClient);
        parallelTracks = args.getArgumentValue("--tracks", "-t", parallelTracks);
        cyclesPerTrack = args.getArgumentValue("--cycles", "-c", cyclesPerTrack);
        sessionsPerTrack = args.getArgumentValue("--sessions", "-s", sessionsPerTrack);
        durationNanos = args.getArgumentValue("--duration", "-d", durationNanos);
    
        telemetryPort = args.getArgumentValue("--telemPort", "-tp", telemetryPort);
        telemetryHost = args.getArgumentValue("--telemHost", "-th", telemetryHost);
        warmup = args.getArgumentValue("--warmup", "-wu", warmup);
        cycleRate = args.getArgumentValue("--rate", "-ra", cycleRate);
        simultaneousRequestsPerTrackBits = args.getArgumentValue("--simulRquests", "-sr", simultaneousRequestsPerTrackBits);
    }

    public ParallelClientLoadTesterConfig(
            int cyclesPerTrack,
            int port,
            final String routeIn,
            boolean enableTelemetry) {
        this.cyclesPerTrack = cyclesPerTrack;
        this.port = port;
        this.route = new RouteFactory() {
			@Override
			public String route(long callInstance) {
				return routeIn;
			}
        };
        this.telemetryPort = enableTelemetry ? TelemetryConfig.defaultTelemetryPort +1+ (maskPort&nextPort.getAndIncrement()) : null;

    }

    private static AtomicInteger nextPort = new AtomicInteger();
    private static int maskPort = 0xFF;//only rotate over 256;
    
    public ParallelClientLoadTesterConfig(
            int parallelTracks,
            int cyclesPerTrack,
            int port,
            String routeIn,
            boolean enableTelemetry) {
        this.parallelTracks = parallelTracks;
        this.cyclesPerTrack = cyclesPerTrack;
        this.port = port;
        this.route = new RouteFactory() {
			@Override
			public String route(long callInstance) {
				return routeIn;
			}
        };
        this.telemetryPort = enableTelemetry ? TelemetryConfig.defaultTelemetryPort +1+ (maskPort&nextPort.getAndIncrement()) : null;

    }
    
    public ParallelClientLoadTesterConfig(
            int parallelTracks,
            int cyclesPerTrack,
            int port,
            RouteFactory routeFactory,
            boolean enableTelemetry) {
        this.parallelTracks = parallelTracks;
        this.cyclesPerTrack = cyclesPerTrack;
        this.port = port;
        this.route = routeFactory;
        this.telemetryPort = enableTelemetry ? TelemetryConfig.defaultTelemetryPort +1+ (maskPort&nextPort.getAndIncrement()) : null;

    }
    
    public ParallelClientLoadTesterConfig(
            int parallelTracks,
            int cyclesPerTrack,
            int port,
            String routeIn,
            boolean enableTelemetry, Appendable target) {
        this.parallelTracks = parallelTracks;
        this.cyclesPerTrack = cyclesPerTrack;
        this.port = port;
        this.route = new RouteFactory() {
			@Override
			public String route(long callInstance) {
				return routeIn;
			}
        };
        this.telemetryPort = enableTelemetry ? TelemetryConfig.defaultTelemetryPort +1+ (maskPort&nextPort.getAndIncrement()) : null;

        this.target = target;
    }
    
}
