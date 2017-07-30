package com.ociweb.gl.impl;

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
     * @param reader {@link PayloadReader} for the response body.
     */
	boolean responseHTTP(HTTPResponseReader reader);

}
