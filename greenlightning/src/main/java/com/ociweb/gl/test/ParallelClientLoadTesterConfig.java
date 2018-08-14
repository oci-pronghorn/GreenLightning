package com.ociweb.gl.test;

import java.util.function.Supplier;

import com.ociweb.gl.api.ArgumentProvider;
import com.ociweb.gl.api.TelemetryConfig;

public class ParallelClientLoadTesterConfig {
    public String host = "127.0.0.1";
    public int port = 8080;
    public Supplier<String> route;
    public boolean insecureClient = true;
    public int parallelTracks = 4;
    public int cyclesPerTrack = 1;
    public long durationNanos = 0;
 
    public Integer telemetryPort = null;
    public String telemetryHost = null;
    public int warmup = 0;
    public Long cycleRate = 4000L; //very fast rate
	public int simultaneousRequestsPerTrackBits = 0; // as power of 2. 0 == serial requests on a track
	public Appendable target = System.out;
	
    public ParallelClientLoadTesterConfig() {
    }

    public ParallelClientLoadTesterConfig(ArgumentProvider args) {
        inject(args);
    }

    public void inject(ArgumentProvider args) {
        host = args.getArgumentValue("--host", "-h", host);
        port = args.getArgumentValue("--port", "-p", port);
        String routeIn = args.getArgumentValue("--route", "-r", "");
        route = new Supplier<String>() {
        	public String get() {return routeIn;};
        };
        
        insecureClient = args.getArgumentValue("--insecure", "-is", insecureClient);
        parallelTracks = args.getArgumentValue("--tracks", "-t", parallelTracks);
        cyclesPerTrack = args.getArgumentValue("--cycles", "-c", cyclesPerTrack);
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
        this.route = new Supplier<String>() {
        	public String get() {return routeIn;};
        };
        this.telemetryPort = enableTelemetry ? TelemetryConfig.defaultTelemetryPort + 13 : null;

    }

    public ParallelClientLoadTesterConfig(
            int parallelTracks,
            int cyclesPerTrack,
            int port,
            String routeIn,
            boolean enableTelemetry) {
        this.parallelTracks = parallelTracks;
        this.cyclesPerTrack = cyclesPerTrack;
        this.port = port;
        this.route = new Supplier<String>() {
        	public String get() {return routeIn;};
        };
        this.telemetryPort = enableTelemetry ? TelemetryConfig.defaultTelemetryPort + 13 : null;

    }
    
    public ParallelClientLoadTesterConfig(
            int parallelTracks,
            int cyclesPerTrack,
            int port,
            Supplier<String> routeSupplier,
            boolean enableTelemetry) {
        this.parallelTracks = parallelTracks;
        this.cyclesPerTrack = cyclesPerTrack;
        this.port = port;
        this.route = routeSupplier;
        this.telemetryPort = enableTelemetry ? TelemetryConfig.defaultTelemetryPort + 13 : null;

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
        this.route = new Supplier<String>() {
        	public String get() {return routeIn;};
        };
        this.telemetryPort = enableTelemetry ? TelemetryConfig.defaultTelemetryPort + 13 : null;

        this.target = target;
    }
    
}