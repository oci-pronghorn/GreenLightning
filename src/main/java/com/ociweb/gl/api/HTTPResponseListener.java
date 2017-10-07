package com.ociweb.gl.api;

import com.ociweb.gl.impl.HTTPResponseListenerBase;
import com.ociweb.gl.impl.PayloadReader;
import com.ociweb.pronghorn.network.config.HTTPContentType;

/**
 * Functional interface for HTTP responses returned from outgoing
 * HTTP requests.
 *
 * @author Nathan Tippy
 */
@FunctionalInterface
public interface HTTPResponseListener extends Behavior, HTTPResponseListenerBase {

	

}
