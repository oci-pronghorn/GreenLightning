package com.ociweb.gl.test;

import com.ociweb.gl.api.TelemetryConfig;

public class ParallelClientLoadTesterConfig {
    public String host = "127.0.0.1";
    public int port = 8080;
    public String route = "";
    public boolean insecureClient=true;
    public int parallelTracks = 4;
    public int cyclesPerTrack = 1;
    public long durationNanos = 0;
    public long responseTimeoutNS = 100_000_000;
    public Integer telemetryPort = null;
    public String telemetryHost = null;
    public Long rate = null;
	public boolean ensureLowLatency=false;
	public int simultaneousRequestsPerTrack = 0; // as power of 2. 0 == serial requests opn a track

    public ParallelClientLoadTesterConfig() {
    }

    public ParallelClientLoadTesterConfig(
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

    public ParallelClientLoadTesterConfig(
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
