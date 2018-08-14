package com.ociweb.gl.impl;

import com.ociweb.pronghorn.network.ServerCoordinator;


public interface RestMethodListenerBase {

	public static final int END_OF_RESPONSE = ServerCoordinator.END_RESPONSE_MASK;
	public static final int CLOSE_CONNECTION = ServerCoordinator.CLOSE_CONNECTION_MASK;

    
}
