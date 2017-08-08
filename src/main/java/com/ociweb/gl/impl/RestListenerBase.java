package com.ociweb.gl.impl;

import com.ociweb.gl.api.HTTPRequestReader;
import com.ociweb.gl.api.RestMethodListener;
import com.ociweb.pronghorn.network.ServerCoordinator;

/**
 * Functional interface for a handler of REST service events.
 *
 * @author Nathan Tippy
 */
@FunctionalInterface
public interface RestListenerBase extends RestMethodListener{

	public static final int END_OF_RESPONSE = ServerCoordinator.END_RESPONSE_MASK;
	public static final int CLOSE_CONNECTION = ServerCoordinator.CLOSE_CONNECTION_MASK;
		

    boolean restRequest(HTTPRequestReader request);
    
    
}
