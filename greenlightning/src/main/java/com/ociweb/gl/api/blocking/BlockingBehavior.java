package com.ociweb.gl.api.blocking;

import com.ociweb.pronghorn.pipe.ChannelReader;
import com.ociweb.pronghorn.pipe.ChannelWriter;

public abstract class BlockingBehavior {

	public abstract void begin(ChannelReader reader);
	
	public abstract void run() throws InterruptedException;
	
	public abstract void finish(ChannelWriter writer);
	public abstract void timeout(ChannelWriter writer);

	public String name() { //override this to add a custom name to this blockable
		return "BlockingTask"; 
	}

	public int requestedStackSize() {
		return 0; //zero is ignored so the default stack will be used.
	}
	
}
