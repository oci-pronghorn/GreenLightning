package com.ociweb.gl.api;

import com.ociweb.gl.impl.PayloadReader;
import com.ociweb.pronghorn.network.config.HTTPContentType;

/**
 * Functional interface for HTTP responses returned from outgoing
 * HTTP requests.
 *
 * @author Nathan Tippy
 */
@FunctionalInterface
public interface HTTPResponseListener {

    /**
     * Invoked when an HTTP response is received by this listener.
     *
     * @param host Host the response was received from.
     * @param port Port the response was received from.
     * @param statusCode Status code of the response. -1 indicates
     *                   the network connection was lost.
     * @param type {@link HTTPContentType} of the response.
     * @param reader {@link PayloadReader} for the response body.
     */
	boolean responseHTTP(CharSequence host, int port, short statusCode, HTTPContentType type, PayloadReader reader);

}
