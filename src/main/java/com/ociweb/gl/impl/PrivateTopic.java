package com.ociweb.gl.impl;

import com.ociweb.gl.impl.schema.MessagePrivate;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeConfig;

public class PrivateTopic {

	private Pipe<MessagePrivate>[] p;
	private int activeIndex=0;
	
	public final String topic;
	
	private final PipeConfig<MessagePrivate> config;
	
	public PrivateTopic(String topic, int messageCount, 
			            int messageSize, boolean hideLabels,
			            int parallelismTracks) {
		this.topic = topic;
		this.config = new PipeConfig<MessagePrivate>(MessagePrivate.instance, messageCount, messageSize);		
		if (hideLabels) {
			this.config.hideLabels(); //private topics can clutter if they show all the details.
		}
		this.p = (Pipe<MessagePrivate>[])new Pipe[parallelismTracks];
	}
	
	public PrivateTopic(String topic, PipeConfig<MessagePrivate> config) {
		this.topic = topic;
		this.config = config;
	}

	public Pipe<MessagePrivate> getPipe() {
		Pipe<MessagePrivate> result = p[activeIndex];
		if (null == result) {
			result = p[activeIndex] = PipeConfig.pipe(config);	
		}
		return result;

	}

	public void selectTrack(int parallelInstanceUnderActiveConstruction) {
		//store index so next getPipe() call picks it up
		activeIndex = parallelInstanceUnderActiveConstruction;
	}

	
}

