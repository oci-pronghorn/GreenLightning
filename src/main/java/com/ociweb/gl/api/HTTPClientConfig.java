package com.ociweb.gl.api;

import com.ociweb.pronghorn.network.TLSCertificates;

public interface HTTPClientConfig {
    boolean isTLS();
    TLSCertificates getCertificates();
    ClientHostPortConfig createHTTP1xClient(String host, int port);
}
