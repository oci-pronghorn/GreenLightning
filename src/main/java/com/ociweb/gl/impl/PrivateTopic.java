package com.ociweb.gl.impl;

import com.ociweb.gl.impl.schema.MessagePrivate;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeConfig;

public class PrivateTopic {

	private Pipe<MessagePrivate> p;
	public final String topic;
	
	private final PipeConfig<MessagePrivate> config;
	
	public PrivateTopic(String topic, int messageCount, int messageSize) {
		this.topic = topic;
		this.config = new PipeConfig<MessagePrivate>(MessagePrivate.instance, messageCount, messageSize);		
	}
	
	public PrivateTopic(String topic, PipeConfig<MessagePrivate> config) {
		this.topic = topic;
		this.config = config;
	}

	public Pipe<MessagePrivate> getPipe() {
		if (null==p) {
			p = PipeConfig.pipe(config);			
		}
		return p;
	}
	
}

