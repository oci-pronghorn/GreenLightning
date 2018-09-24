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
    private int maxSimultaniousRequests = 1024;//default max HTTP calls to clientSocketWriter 
    

    
    private BridgeConfigStage configStage = BridgeConfigStage.Construction;
    private  PipeConfigManager pcm;

    public HTTPClientConfigImpl(TLSCertificates certificates, PipeConfigManager pcm) {
        this.certificates = certificates;
        this.pcm = pcm;
		
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
    
	public int getUnwrapCount() {
		return unwrapCount;
	}

	public int getMaxRequestSize() {
		return maxRequestSize;
	}

	public int getMaxSimultaniousRequests() {
		return maxSimultaniousRequests;
	}
	
	@Override
	public HTTPClientConfig setUnwrapCount(int unwrapCount) {
		assert(unwrapCount>0);
		this.unwrapCount = unwrapCount;
		return this;
	}
	
    @Override
    public HTTPClientConfig setMaxSimultaniousRequests(int value) {
    	maxSimultaniousRequests = value;
    	return this;
    }
    
    @Override
    public HTTPClientConfig setMaxResponseSize(int value) {
    	this.pcm.ensureSize(NetResponseSchema.class, 4, value);
    	return this;
    }
    
    @Override
    public HTTPClientConfig setMaxRequestSize(int value) {
    	maxRequestSize = value;
    	return this;
    }       
    
    @Override
    public HTTPClientConfig setResponseQueueLength(int value) {    
    	this.pcm.ensureSize(NetResponseSchema.class, value, 32);  
    	return this;
    }   
}
