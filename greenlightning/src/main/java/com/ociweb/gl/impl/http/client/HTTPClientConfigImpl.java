package com.ociweb.gl.impl.http.client;

import com.ociweb.gl.api.ClientHostPortConfig;
import com.ociweb.gl.api.HTTPClientConfig;
import com.ociweb.gl.impl.BridgeConfigStage;
import com.ociweb.pronghorn.network.TLSCertificates;
import com.ociweb.pronghorn.network.schema.NetResponseSchema;
import com.ociweb.pronghorn.pipe.PipeConfigManager;

public class HTTPClientConfigImpl implements HTTPClientConfig {
    private TLSCertificates certificates;
    
    private int unwrapCount = 2;//default
    private int maxRequestSize = 512;//default
    
    //TODO: too large must expose for load tester, inital call will be inflight * tracks..
    //TODO: we have 1 pipe per each so we only need 1024
    private int maxSimultaniousRequests = 1024;//default max HTTP verbs to clientSocketWriter
    //TODO: for the max size why is this 128M
    
	
    private BridgeConfigStage configStage = BridgeConfigStage.Construction;
    private  PipeConfigManager pcm;

    public HTTPClientConfigImpl(TLSCertificates certificates, PipeConfigManager pcm) {
        this.certificates = certificates;
        this.pcm = pcm;
		
        //TODO: should expose, this allows large numbers of responses from HTTP1xResponse parser to be dumped to consumers 
        int queueLength = 1024;  // HIGHVOLUME switch need for load testing product
		int maxMessageSize = 128;
		this.pcm.ensureSize(NetResponseSchema.class, queueLength, maxMessageSize);
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

	public int getMaxRequestSize() {
		return maxRequestSize;
	}

	public int getMaxSimultaniousRequests() {
		return maxSimultaniousRequests;
	}
}
