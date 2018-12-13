package com.ociweb.gl.api;

import com.ociweb.gl.impl.BuilderImpl;
import com.ociweb.pronghorn.pipe.PipeConfigManager;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;

public class GreenCommandChannel extends MsgCommandChannel<BuilderImpl> {

	/**
	 *
	 * @param builder arg of data type BuilderImpl
	 * @param features int arg
	 * @param parallelInstanceId int arg
	 * @param pcm arg of data type PipeConfigManager
	 */
	public GreenCommandChannel(BuilderImpl builder, int features, int parallelInstanceId,
			PipeConfigManager pcm) {
		super(builder, features, parallelInstanceId, pcm);
	}

}
