package com.ociweb.gl.test;

import com.ociweb.gl.api.ArgumentProvider;
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

    public ParallelClientLoadTesterConfig(ArgumentProvider args) {
        host = args.getArgumentValue("--host", "-h", host);
        port = Integer.parseInt(args.getArgumentValue("--port", "-p", Integer.toString(port)));
        route = args.getArgumentValue("--route", "-r", route);
        insecureClient = args.getArgumentValue("--insecure", "-is", insecureClient);
        parallelTracks = args.getArgumentValue("--tracks", "-t", parallelTracks);
        cyclesPerTrack = args.getArgumentValue("--cycles", "-c", cyclesPerTrack);
        durationNanos = args.getArgumentValue("--duration", "-d", durationNanos);
        responseTimeoutNS = args.getArgumentValue("--timeout", "-to", responseTimeoutNS);
        telemetryPort = args.getArgumentValue("--telemPort", "-tp", telemetryPort);
        telemetryHost = args.getArgumentValue("--telemHost", "-th", telemetryHost);
        rate = args.getArgumentValue("--rate", "-r", rate);
        ensureLowLatency = args.getArgumentValue("--lowLatency", "-ll", ensureLowLatency);
        simultaneousRequestsPerTrack = args.getArgumentValue("--simulRquests", "-s", simultaneousRequestsPerTrack);
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
