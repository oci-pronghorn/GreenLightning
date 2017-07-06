package com.ociweb.gl.api;

/**
 * Functional interface for a listener for time events triggered
 * by the {@link MsgRuntime}.
 *
 * @author Nathan Tippy
 */
@FunctionalInterface
public interface TimeListener extends Behavior {

    /**
     * Invoked when a time event is received from the {@link MsgRuntime}.
     *
     * @param time Time of the event in milliseconds since the UNIX epoch.
     */
    void timeEvent(long time, int iteration);
}
