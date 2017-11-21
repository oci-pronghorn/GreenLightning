package com.ociweb.gl.impl.http.server;

import com.ociweb.gl.api.HTTPResponseReader;
import com.ociweb.pronghorn.network.config.HTTPContentType;


/**
 * Functional interface for HTTP responses returned from outgoing
 * HTTP requests.
 *
 * @author Nathan Tippy
 */
@FunctionalInterface
public interface HTTPResponseListenerBase {
    /**
     * Invoked when an HTTP response is received by this listener.
     * 
     */
	boolean responseHTTP(HTTPResponseReader reader);

}
