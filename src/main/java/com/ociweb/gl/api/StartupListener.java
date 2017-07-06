package com.ociweb.gl.api;

/**
 * Functional interface that can be registered with a {@link MsgRuntime}
 * to receive a single event when the device starts.
 *
 * @author Nathan Tippy
 */
@FunctionalInterface
public interface StartupListener extends Behavior {

    /**
     * Invoked once when the {@link MsgRuntime} starts up the IoT application.
     */
    void startup();
    
}
