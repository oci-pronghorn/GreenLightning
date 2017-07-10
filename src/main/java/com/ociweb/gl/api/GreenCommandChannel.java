package com.ociweb.gl.api;

import com.ociweb.gl.impl.BuilderImpl;
import com.ociweb.pronghorn.pipe.PipeConfigManager;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;

public class GreenCommandChannel extends MsgCommandChannel<BuilderImpl> {

	public GreenCommandChannel(GraphManager gm, BuilderImpl builder, int features, int parallelInstanceId,
			PipeConfigManager pcm) {
		super(gm, builder, features, parallelInstanceId, pcm);
	}

}
