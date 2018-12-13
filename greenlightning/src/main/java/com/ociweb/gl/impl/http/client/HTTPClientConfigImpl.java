package com.ociweb.gl.impl.http.client;

import com.ociweb.gl.api.ClientHostPortConfig;
import com.ociweb.gl.api.HTTPClientConfig;
import com.ociweb.gl.impl.BridgeConfigStage;
import com.ociweb.pronghorn.network.TLSCertificates;
import com.ociweb.pronghorn.network.schema.NetPayloadSchema;
import com.ociweb.pronghorn.network.schema.NetResponseSchema;
import com.ociweb.pronghorn.pipe.PipeConfigManager;

public class HTTPClientConfigImpl implements HTTPClientConfig {
    private TLSCertificates certificates;
    
    private int unwrapCount = 2;//default
    private int concurentPipesPerWriter = 2;
    private int socketWriterCount = 2;

    
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

	
	@Override
	public HTTPClientConfig setUnwrapCount(int unwrapCount) {
		assert(unwrapCount>0);
		this.unwrapCount = unwrapCount;
		return this;
	}
    
    @Override
    public HTTPClientConfig setMaxResponseSize(int value) {
    	//System.out.println("response size: "+value);
    	this.pcm.ensureSize(NetResponseSchema.class, 4, value);
    	return this;
    }
 
    
    @Override
    public HTTPClientConfig setResponseQueueLength(int value) {    
    	this.pcm.ensureSize(NetResponseSchema.class, value, 32);  
    	return this;
    }

	public int getConcurentPipesPerWriter() {
		return concurentPipesPerWriter;
	}   
	
	@Override
	public HTTPClientConfig setConcurentPipesPerWriter(int value) {
		concurentPipesPerWriter = value;
		return this;
	}
	
    @Override
    public HTTPClientConfig setRequestQueueLength(int value) {    
    	this.pcm.ensureSize(NetPayloadSchema.class, value, 32);  
    	return this;
    }
	
    @Override
    public HTTPClientConfig setMaxRequestSize(int value) {
    	this.pcm.ensureSize(NetPayloadSchema.class, 4, value);
    	//System.out.println("set max size: "+value+" maxVar:"+pcm.getConfig(NetPayloadSchema.class).maxVarLenSize()+" "+pcm.getConfig(NetPayloadSchema.class));
    	return this;
    }

	public int getSocketWriterCount() {
		return socketWriterCount;
	}
	
	@Override
    public HTTPClientConfig setSocketWriterCount(int value) {
	    this.socketWriterCount = value;
	    return this;
	}

	public int getReleaseCount() {
		return 1024;
	}

	public int getResponseQueue() {
		return 32;
	}

	public int getNetResponseCount() { //needed for heavy load tests to consume all the responses when they arrive.
		return 512;
	}
	
	
	
}
