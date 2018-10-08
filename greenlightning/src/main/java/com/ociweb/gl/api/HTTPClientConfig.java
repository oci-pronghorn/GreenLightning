package com.ociweb.gl.api;

import com.ociweb.pronghorn.network.TLSCertificates;

public interface HTTPClientConfig {
	
    boolean isTLS();
    TLSCertificates getCertificates();
    ClientHostPortConfig newHTTPSession(String host, int port);
    HTTPClientConfig setUnwrapCount(int unwrapCount);
	HTTPClientConfig setMaxResponseSize(int value);
	HTTPClientConfig setMaxRequestSize(int value);
	HTTPClientConfig setResponseQueueLength(int value);
	HTTPClientConfig setConcurentPipesPerWriter(int value);
	HTTPClientConfig setRequestQueueLength(int value);
	HTTPClientConfig setSocketWriterCount(int value);    
    
}
