package com.ociweb.gl.impl;

import com.ociweb.gl.impl.schema.MessagePrivate;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeConfig;
import com.ociweb.pronghorn.util.CharSequenceToUTF8Local;

public class PrivateTopic {

	private Pipe<MessagePrivate>[] p;
	
	public final String topic;
	private final BuilderImpl builder;
	
	private final PipeConfig<MessagePrivate> config;
	
	public PrivateTopic(String topic, int messageCount, 
			            int messageSize, boolean hideLabels,
			            BuilderImpl builder) {
		this.topic = topic;
		this.config = new PipeConfig<MessagePrivate>(MessagePrivate.instance, messageCount, messageSize);		
		if (hideLabels) {
			this.config.hideLabels(); //private topics can clutter if they show all the details.
		}
		this.builder = builder;
	}
	
	public PrivateTopic(String topic, PipeConfig<MessagePrivate> config, boolean hideLabels,
            BuilderImpl builder) {
		this.topic = topic;
		this.config = config;
		this.builder = builder;
	}
	
	public PrivateTopic(String topic, PipeConfig<MessagePrivate> config, BuilderImpl builder) {
		this.topic = topic;
		this.config = config;
		this.builder = builder;
	}

	private int maxIndex = Integer.MAX_VALUE;

	public int customDispatchId = -2; //-2 indicates that this cache is still empty
	
	public Pipe<MessagePrivate> getPipe(int activeIndex) {
		if (null==p) {
			p = new Pipe[builder.parallelTracks()];
		}
		
		if (activeIndex>maxIndex) {
			throw new UnsupportedOperationException("can not span between primary and tracks with private topic");			
		}
		if (activeIndex<0) {
			//confirm that this topic was never used for tracks since we have a single request
			for(int i = 1; i<p.length; i++) {
				if (null!=p[i]) {
					throw new UnsupportedOperationException("can not span between primary and tracks with private topic");
				}
			}
			maxIndex = 0;
			activeIndex = 0;
		}
		
		
		Pipe<MessagePrivate> result = p[activeIndex];
		if (null == result) {
			result = p[activeIndex] = PipeConfig.pipe(config);
		}
		return result;

	}

	public void populatePrivateTopicPipeNames(byte[][] names) {
		
		byte[] topicBytes = topic.getBytes();
		int x = p.length;
		while (--x>=0) {
			Pipe pipe = p[x];
			if (null!=pipe) {
				names[pipe.id] = topicBytes;
			}			
		}
	}

	
}

