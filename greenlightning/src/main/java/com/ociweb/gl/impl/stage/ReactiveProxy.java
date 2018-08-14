package com.ociweb.gl.impl.stage;

import com.ociweb.gl.impl.schema.TrafficOrderSchema;
import com.ociweb.pronghorn.pipe.Pipe;

public abstract class ReactiveProxy {

	public abstract void startup();
	
	public abstract void run();
	
	public abstract void shutdown();

	public abstract int getFeatures(Pipe<TrafficOrderSchema> orderPipe);
		
}
