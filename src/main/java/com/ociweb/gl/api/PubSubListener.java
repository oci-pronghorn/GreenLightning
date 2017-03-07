package com.ociweb.gl.api;

/**
 * Functional interface for a publish-subscribe subscriber registered
 * with the {@link GreenRuntime}.
 *
 * @author Nathan Tippy
 */
@FunctionalInterface
public interface PubSubListener {

    /**
     * Invoked when a new publication is received from the {@link GreenRuntime}.
     *
     * @param topic Topic of the publication.
     * @param payload {@link PayloadReader} for the topic contents.
     */
    boolean message(CharSequence topic, PayloadReader payload);
}
