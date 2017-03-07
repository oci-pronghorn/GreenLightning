package com.ociweb.gl.api;

/**
 * Functional interface that can be registered with a {@link GreenRuntime}
 * to receive a single event when the device starts.
 *
 * @author Nathan Tippy
 */
@FunctionalInterface
public interface StartupListener {

    /**
     * Invoked once when the {@link GreenRuntime} starts up the IoT application.
     */
    void startup();
    
}
