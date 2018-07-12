package com.ociweb.gl.impl;

import com.ociweb.gl.api.MsgRuntime;

public interface TimeListenerBase {
    /**
     * Invoked when a time event is received from the {@link MsgRuntime}.
     *
     * @param time Time of the event in milliseconds since the UNIX epoch.
     */
    void timeEvent(long time, int iteration);
}
