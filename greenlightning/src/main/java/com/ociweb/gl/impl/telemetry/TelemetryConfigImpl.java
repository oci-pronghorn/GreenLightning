package com.ociweb.gl.impl.telemetry;

import com.ociweb.gl.api.TelemetryConfig;
import com.ociweb.gl.impl.BridgeConfigStage;
import com.ociweb.pronghorn.network.NetGraphBuilder;

public class TelemetryConfigImpl implements TelemetryConfig {
    private String host;
    private int port = TelemetryConfig.defaultTelemetryPort;

    public TelemetryConfigImpl(String host, int port) {
        this.port = port;
        this.host = host;
    }

    @Override
    public String getHost() {
        return host;
    }

    @Override
    public int getPort() {
        return port;
    }

    public void finalizeDeclareConnections() {
    	this.host = port>0?NetGraphBuilder.bindHost(this.host):null; //if port is positive ensure we have the right host
    }
}
