package com.ociweb.oe.greenlightning.api;

import java.util.Random;

import com.ociweb.gl.api.MessageReader;
import com.ociweb.gl.api.PubSubListener;
import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.pronghorn.pipe.BlobReader;
import com.ociweb.pronghorn.util.Appendables;

public class GenerateBehavior implements PubSubListener {

	Random rand;
    private final CharSequence publishTopic;
	final GreenCommandChannel channel1;
	private final Appendable target;
	
	public GenerateBehavior(GreenRuntime runtime, CharSequence publishTopic, Appendable target, int seed) {
		
		this.target = target;
		channel1 = runtime.newCommandChannel(DYNAMIC_MESSAGING);
		
		this.publishTopic = publishTopic;
		this.rand = new Random(seed);
	}


	@Override
	public boolean message(CharSequence topic, BlobReader payload) {
		int n = rand.nextInt(101);
		Appendables.appendValue(target, "", n, " ");
		
		return channel1.publishTopic(publishTopic);
		
	}

}
