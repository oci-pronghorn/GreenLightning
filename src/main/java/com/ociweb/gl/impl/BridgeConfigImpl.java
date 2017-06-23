package com.ociweb.gl.impl;

import com.ociweb.gl.api.BridgeConfig;

public abstract class BridgeConfigImpl implements BridgeConfig {

	@Override
	public BridgeConfig addSubscription(CharSequence topic) {
		return addSubscription(topic,topic);
	}

	@Override
	public BridgeConfig addTransmission(CharSequence topic) {
		return addTransmission(topic,topic);
	}


	
}
