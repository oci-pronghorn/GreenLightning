package com.ociweb.gl.api;

public interface MQTTBridge extends BridgeConfig {

	public MQTTBridge keepAliveSeconds(int seconds);
	public MQTTBridge cleanSession(boolean clean);
	public MQTTBridge authentication(CharSequence user, CharSequence pass);
	public MQTTBridge will(boolean retrain, int qos, CharSequence topic, MQTTWritable write );
	public MQTTBridge subscriptionQoS(int value);
	public MQTTBridge transmissionOoS(int value);
	public MQTTBridge transmissionRetain(boolean value);
	
}
