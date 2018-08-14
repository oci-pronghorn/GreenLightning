package com.ociweb.gl.api;

public interface TelemetryConfig {
    int defaultTelemetryPort = 8098;

    String getHost();

    int getPort();
}
