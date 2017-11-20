package com.ociweb.gl.impl.http.client;

import com.ociweb.gl.api.HTTPClientConfig;
import com.ociweb.gl.impl.BridgeConfigStage;
import com.ociweb.pronghorn.network.TLSCertificates;

public class HTTPClientConfigImpl implements HTTPClientConfig {
    private TLSCertificates certificates;
    //TODO: set these vales when we turn on the client usage??
    private int connectionsInBit = 3;
    private int maxPartialResponse = 10;
    private BridgeConfigStage configStage = BridgeConfigStage.Construction;

    public HTTPClientConfigImpl(TLSCertificates certificates) {
        this.certificates = certificates;
    }

    @Override
    public boolean isTLS() {
        return certificates != null;
    }

    @Override
    public TLSCertificates getCertificates() {
        return certificates;
    }

    public void beginDeclarations() {
        this.configStage = BridgeConfigStage.DeclareConnections;
    }

    public void finalizeDeclareConnections() {
        this.configStage = BridgeConfigStage.DeclareBehavior;
    }
}
