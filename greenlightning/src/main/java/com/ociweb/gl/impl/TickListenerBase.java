package com.ociweb.gl.impl;

public interface TickListenerBase {
    /**
     * Invoked when run is called in reactive listener.  This is driven by the rate of the stage and may be 
     * called often.  This is only to be used for light weight polling which may trigger periodically.
     * 
     */
    void tickEvent();
}
