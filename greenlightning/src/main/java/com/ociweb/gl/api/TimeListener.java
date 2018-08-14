package com.ociweb.gl.api;

import com.ociweb.gl.impl.TimeListenerBase;

/**
 * Functional interface for a listener for time events triggered
 * by the {@link MsgRuntime}.
 *
 * @author Nathan Tippy
 */
@FunctionalInterface
public interface TimeListener extends Behavior, TimeListenerBase {


}
