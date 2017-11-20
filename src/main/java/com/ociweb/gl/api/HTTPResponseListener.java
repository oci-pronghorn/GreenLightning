package com.ociweb.gl.api;

import com.ociweb.gl.impl.http.server.HTTPResponseListenerBase;

/**
 * Functional interface for HTTP responses returned from outgoing
 * HTTP requests.
 *
 * @author Nathan Tippy
 */
@FunctionalInterface
public interface HTTPResponseListener extends Behavior, HTTPResponseListenerBase {

	

}
