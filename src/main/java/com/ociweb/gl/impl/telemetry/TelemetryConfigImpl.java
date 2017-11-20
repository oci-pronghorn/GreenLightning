package com.ociweb.gl.impl.telemetry;

import com.ociweb.gl.api.TelemetryConfig;
import com.ociweb.gl.impl.BridgeConfigStage;
import com.ociweb.pronghorn.network.NetGraphBuilder;

public class TelemetryConfigImpl implements TelemetryConfig {
    private String host;
    private int port = TelemetryConfig.defaultTelemetryPort;
    private BridgeConfigStage configStage = BridgeConfigStage.Construction;

    public TelemetryConfigImpl(String host, int port) {
        this.port = port;
        this.host = host;
    }

    public void beginDeclarations() {
        this.configStage = BridgeConfigStage.DeclareConnections;
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
        if (null == this.getHost()) {
            this.host = NetGraphBuilder.bindHost();
        }
        this.configStage = BridgeConfigStage.DeclareBehavior;
    }
}
