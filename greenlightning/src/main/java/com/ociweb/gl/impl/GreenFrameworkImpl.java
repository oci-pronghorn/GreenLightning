package com.ociweb.gl.impl;

import com.ociweb.gl.api.GreenFramework;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;

public class GreenFrameworkImpl extends BuilderImpl<GreenRuntime> implements GreenFramework {

	public GreenFrameworkImpl(GraphManager gm, String[] args) {
		super(gm, args);
	}

}
