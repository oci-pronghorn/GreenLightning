package com.ociweb.gl.impl.blocking;

import com.ociweb.pronghorn.pipe.ChannelReader;

public interface TargetSelector {

	int pickTargetIdx(ChannelReader p);

}
