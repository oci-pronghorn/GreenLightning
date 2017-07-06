package com.ociweb.gl.api;

import com.ociweb.gl.impl.stage.EgressConverter;
import com.ociweb.gl.impl.stage.IngressConverter;

public interface BridgeConfig {

	BridgeConfig addSubscription(CharSequence topic);
	BridgeConfig addSubscription(CharSequence internalTopic, CharSequence externalTopic);
	BridgeConfig addSubscription(CharSequence internalTopic, CharSequence externalTopic, IngressConverter converter);
	
	BridgeConfig addTransmission(MsgRuntime<?,?> msgRuntime, CharSequence topic);
	BridgeConfig addTransmission(MsgRuntime<?,?> msgRuntime, CharSequence internalTopic, CharSequence externalTopic);
	BridgeConfig addTransmission(MsgRuntime<?,?> msgRuntime, CharSequence internalTopic, CharSequence externalTopic, EgressConverter converter);
	
}
