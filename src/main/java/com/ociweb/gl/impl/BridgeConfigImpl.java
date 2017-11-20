package com.ociweb.gl.impl;

import com.ociweb.gl.api.BridgeConfig;
import com.ociweb.gl.api.MsgRuntime;

public abstract class BridgeConfigImpl<T,S> implements BridgeConfig<T,S> {

	@Override
	public long addSubscription(CharSequence topic) {
		return addSubscription(topic,topic);
	}

	@Override
	public long addTransmission(MsgRuntime<?,?> msgRuntime, CharSequence topic) {
		return addTransmission(msgRuntime, topic,topic);
	}

	public abstract void finalizeDeclareConnections(MsgRuntime<?,?> msgRuntime);
	
}
