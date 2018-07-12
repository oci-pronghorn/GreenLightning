package com.ociweb.gl.api;

import com.ociweb.gl.impl.TickListenerBase;

/**
 * Functional interface for a listener for ticks every time the run in reactive listener is processed
 * by the {@link MsgRuntime}.
 *
 * @author Nathan Tippy
 */
@FunctionalInterface
public interface TickListener extends Behavior, TickListenerBase {

}
