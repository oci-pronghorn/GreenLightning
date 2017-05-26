package com.ociweb.gl.api;

import com.ociweb.pronghorn.network.ServerCoordinator;
import com.ociweb.pronghorn.network.config.HTTPHeader;
import com.ociweb.pronghorn.network.schema.HTTPRequestSchema;

/**
 * Functional interface for a handler of REST service events.
 *
 * @author Nathan Tippy
 */
@FunctionalInterface
public interface RestListener {

	public static final int END_OF_RESPONSE = ServerCoordinator.END_RESPONSE_MASK;
	public static final int CLOSE_CONNECTION = ServerCoordinator.CLOSE_CONNECTION_MASK;
		

    boolean restRequest(HTTPRequestReader request);
    
    
}
