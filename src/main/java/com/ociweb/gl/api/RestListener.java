package com.ociweb.gl.api;

import com.ociweb.pronghorn.network.ServerCoordinator;
import com.ociweb.pronghorn.network.config.HTTPVerb;

/**
 * Functional interface for a handler of REST service events.
 *
 * @author Nathan Tippy
 */
@FunctionalInterface
public interface RestListener {

	public static final int END_OF_RESPONSE = ServerCoordinator.END_RESPONSE_MASK;
	public static final int CLOSE_CONNECTION = ServerCoordinator.CLOSE_CONNECTION_MASK;
	
	
    /**
     *
     * @param route
     * @param fieldsInPipe
     */
    boolean restRequest(int routeId, long connectionId, long sequenceCode, HTTPVerb verb, FieldReader request);
    
}
