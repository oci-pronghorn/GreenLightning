package com.ociweb.gl.api;

public interface BridgeConfig {

	BridgeConfig addSubscription(CharSequence topic);
	BridgeConfig addSubscription(CharSequence internalTopic, CharSequence externalTopic);
	
	BridgeConfig addTransmission(MsgRuntime msgRuntime, CharSequence topic);
	BridgeConfig addTransmission(MsgRuntime msgRuntime, CharSequence internalTopic, CharSequence externalTopic);
	
}
