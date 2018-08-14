package com.ociweb.gl.api;

import com.ociweb.gl.impl.StateChangeListenerBase;

public interface StateChangeListener<E extends Enum<E>> extends Behavior, StateChangeListenerBase <E> {
}
