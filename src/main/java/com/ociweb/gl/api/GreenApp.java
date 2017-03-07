package com.ociweb.gl.api;

/**
 * Base interface for a green lightning application.
 *
 * An implementation of this interface should be supplied
 * to {@link GreenRuntime#run(GreenApp)} in order to declare
 * the used features and/or URL templates.
 *
 * @author Nathan Tippy
 */
public interface GreenApp {

    /**
     * Invoked when this GreenApp is asked to declare any configuration it needs.
     *
     * This method should perform all of its config declarations directly on the
     * passed {@link Builder} instance; any other changes will have no
     * effect on the final runtime.
     *
     * @param builder {@link Builder} instance to declare connections on.
     *
     * @see Builder
     */
    void declareConfiguration(Builder builder);

    /**
     * Invoked when this GreenApp is asked to declare any behavior that it has.
     *
     * This method should should perform all of its behavior declarations directly
     * on the passed {@link GreenRuntime} instance; any other changes will have
     * no effect on the final runtime.
     *
     * @param runtime {@link GreenRuntime} instance to declare behavior on.
     *
     * @see GreenRuntime
     */
    void declareBehavior(GreenRuntime runtime);
    
    
    void declareParallelBehavior(GreenRuntime runtime);
    
}
