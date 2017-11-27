package com.ociweb.gl.impl;

import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.RawDataSchema;

public class PrivateTopic {

	private Pipe p;
	
	public final String source;
	public final String target;
	public final String topic;
	
	public PrivateTopic(String source, String target, String topic) {
		this.source = source;
		this.target = target;
		this.topic = topic;
	}

	public Pipe<RawDataSchema> getPipe() {
		if (null==p) {
			p = RawDataSchema.instance.newPipe(10, 1000);//TODO: where to config??			
		}
		return p;
	}
	
	
	
	
}

