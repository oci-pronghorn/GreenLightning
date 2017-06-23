package com.ociweb.gl.api;

public interface BridgeConfig {

	BridgeConfig addSubscription(CharSequence topic);
	BridgeConfig addSubscription(CharSequence internalTopic, CharSequence externalTopic);
	
	BridgeConfig addTransmission(CharSequence topic);
	BridgeConfig addTransmission(CharSequence internalTopic, CharSequence externalTopic);
	
}
