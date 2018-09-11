package com.ociweb.gl.api;

/**
 * Base interface for a green lightning application.
 *
 * @author Nathan Tippy
 */
public interface MsgApp<B extends Builder, G extends MsgRuntime> {
	
	 /**
     * Invoked when this GreenApp is asked to declare any configuration it needs.
     *
     * This method should perform all of its config declarations directly on the
     * passed {@link B} instance; any other changes will have no
     * effect on the final runtime.
     *
     * @param config {@link B} instance to declare connections on.
     *
     * @see Builder
     */ 
    void declareConfiguration(B config);

    /**
     * Invoked when this GreenApp is asked to declare any behavior that it has.
     *
     * This method should should perform all of its behavior declarations directly
     * on the passed {@link G} instance; any other changes will have
     * no effect on the final runtime.
     *
     * @param runtime {@link G} instance to declare behavior on.
     *
     * @see MsgRuntime
     */
    void declareBehavior(G runtime);

}
