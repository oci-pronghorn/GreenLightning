package com.ociweb.gl.api;

/**
 * Functional interface for a listener for time events triggered
 * by the {@link GreenRuntime}.
 *
 * @author Nathan Tippy
 */
@FunctionalInterface
public interface TimeListener {

    /**
     * Invoked when a time event is received from the {@link GreenRuntime}.
     *
     * @param time Time of the event in milliseconds since the UNIX epoch.
     */
    void timeEvent(long time, int iteration);
}
