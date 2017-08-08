package com.ociweb.gl.impl;

import com.ociweb.gl.api.MsgRuntime;
import com.ociweb.gl.api.PubSubMethodListener;
import com.ociweb.pronghorn.pipe.BlobReader;


public interface PubSubListenerBase extends PubSubMethodListener {

    /**
     * Invoked when a new publication is received from the {@link MsgRuntime}.
     *
     * @param topic Topic of the publication.
     * @param payload {@link BlobReader} for the topic contents.
     */
    boolean message(CharSequence topic, BlobReader payload);
}
