package com.ociweb.gl.api;

/**
 * Functional interface for a publish-subscribe subscriber registered
 * with the {@link MsgRuntime}.
 *
 * @author Nathan Tippy
 */
@FunctionalInterface
public interface PubSubListener extends Behavior {

    /**
     * Invoked when a new publication is received from the {@link MsgRuntime}.
     *
     * @param topic Topic of the publication.
     * @param payload {@link MessageReader} for the topic contents.
     */
    boolean message(CharSequence topic, MessageReader payload);
}
