package com.ociweb.gl.impl;

import com.ociweb.gl.api.BridgeConfig;
import com.ociweb.gl.api.MsgRuntime;

public abstract class BridgeConfigImpl implements BridgeConfig {

	@Override
	public BridgeConfig addSubscription(CharSequence topic) {
		return addSubscription(topic,topic);
	}

	@Override
	public BridgeConfig addTransmission(MsgRuntime<?,?> msgRuntime, CharSequence topic) {
		return addTransmission(msgRuntime, topic,topic);
	}


	
}
