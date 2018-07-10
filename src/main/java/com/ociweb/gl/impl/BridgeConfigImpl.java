package com.ociweb.gl.impl;

import com.ociweb.gl.api.BridgeConfig;
import com.ociweb.gl.api.MsgRuntime;
import com.ociweb.gl.impl.stage.EgressConverter;
import com.ociweb.gl.impl.stage.IngressConverter;

public abstract class BridgeConfigImpl<T,S> implements BridgeConfig<T,S> {

	public abstract long addSubscription(CharSequence internalTopic, CharSequence externalTopic);
	public abstract long addSubscription(CharSequence internalTopic, CharSequence externalTopic, IngressConverter converter);
	
	public abstract long addTransmission(MsgRuntime<?,?> msgRuntime, CharSequence internalTopic, CharSequence externalTopic);
	public abstract long addTransmission(MsgRuntime<?,?> msgRuntime, CharSequence internalTopic, CharSequence externalTopic, EgressConverter converter);
	
	public long addSubscription(CharSequence topic) {
		return addSubscription(topic,topic);
	}

	public long addTransmission(MsgRuntime<?,?> msgRuntime, CharSequence topic) {
		return addTransmission(msgRuntime, topic,topic);
	}

	public abstract void finalizeDeclareConnections(MsgRuntime<?,?> msgRuntime);
	
}
