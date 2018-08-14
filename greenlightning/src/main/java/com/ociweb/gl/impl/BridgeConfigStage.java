package com.ociweb.gl.impl;

/*
    Bridges exist and can be mutated in different ways in the different
    stages of app setup.
    This is the initial refactoring to be able to enforce mutation rules
    at different stages. Previously we had a boolean only protecting
    declare connection mutations from invocations in declare behavior.
 */
public enum BridgeConfigStage {
    Construction,
    DeclareConnections,
    DeclareBehavior,
    Finalized;

    /**
     *
     * @param stage BridgeConfigStage arg used for comparison
     * @throws UnsupportedOperationException if stage != this
     */
    public void throwIfNot(BridgeConfigStage stage) {
		if (stage != this) {
			throw new UnsupportedOperationException("Cannot invoke method in " + this.toString() + "; must be in " + stage.toString());
		}
    }
}
