package com.ociweb.gl.impl.http.client;

import com.ociweb.gl.api.ClientHostPortConfig;
import com.ociweb.gl.api.HTTPClientConfig;
import com.ociweb.gl.impl.BridgeConfigStage;
import com.ociweb.pronghorn.network.TLSCertificates;

public class HTTPClientConfigImpl implements HTTPClientConfig {
    private TLSCertificates certificates;
    private int unwrapCount = 2;//default
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

    @Override
    public ClientHostPortConfig newHTTPSession(String host, int port) {   
        return new ClientHostPortConfig(host, port);
    }

    public void beginDeclarations() {
        this.configStage = BridgeConfigStage.DeclareConnections;
    }

    public void finalizeDeclareConnections() {
        this.configStage = BridgeConfigStage.DeclareBehavior;
    }

    public void setUnwrapCount(int unwrapCount) {
    	assert(unwrapCount>0);
    	this.unwrapCount = unwrapCount;
    }
    
	public int getUnwrapCount() {
		return unwrapCount;
	}
}
