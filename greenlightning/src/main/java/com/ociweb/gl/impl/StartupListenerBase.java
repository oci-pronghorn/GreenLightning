package com.ociweb.gl.impl;

import com.ociweb.gl.api.MsgRuntime;

/**
 * Functional interface that can be registered with a {@link MsgRuntime}
 * to receive a single event when the device starts.
 *
 * @author Nathan Tippy
 */
public interface StartupListenerBase {
	/**
	 * Invoked once when the {@link MsgRuntime} starts up the IoT application.
	 */
	void startup();
}
