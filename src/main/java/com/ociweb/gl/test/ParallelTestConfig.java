package com.ociweb.gl.test;

import com.ociweb.gl.api.TelemetryConfig;

public class ParallelTestConfig {
    public int parallelTracks = 4;
    public int cyclesPerTrack = 1;
    public String host = "127.0.0.1";
    public int port = 8080;
    public String route = "";
    public long durationNanos = 0;
    public long responseTimeoutNS = 100_000_000;
    public int ignoreInitialPerTrack = 0;
    public Integer telemetryPort = null;
    public String telemetryHost = null;
    public Long rate = null;
	public boolean insecureClient=true;
	public boolean ensureLowLatency=false;
	public int inFlightBits = 0;//only 1 in flight is zero

    public ParallelTestConfig() {
    }

    public ParallelTestConfig(
            int cyclesPerTrack,
            int port,
            String route,
            boolean enableTelemetry) {
        this.cyclesPerTrack = cyclesPerTrack;
        this.port = port;
        this.route = route;
        this.telemetryPort = enableTelemetry ? TelemetryConfig.defaultTelemetryPort + 13 : null;
        this.responseTimeoutNS = 0;
    }

    public ParallelTestConfig(
            int parallelTracks,
            int cyclesPerTrack,
            int port,
            String route,
            boolean enableTelemetry) {
        this.parallelTracks = parallelTracks;
        this.cyclesPerTrack = cyclesPerTrack;
        this.port = port;
        this.route = route;
        this.telemetryPort = enableTelemetry ? TelemetryConfig.defaultTelemetryPort + 13 : null;
        this.responseTimeoutNS = 0;
    }
}
