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
     * @param statusCode Status code of the response. -1 indicates
     *                   the network connection was lost.
     *                   if the status code is 0 this is a continuation.
     * @param type {@link HTTPContentType} of the response, will be null for continuation
     * @param reader {@link PayloadReader} for the response body.
     */
	boolean responseHTTP(short statusCode, HTTPContentType type, HTTPResponseReader reader);
	
}
