package com.ociweb.gl.api;

public interface MQTTConfig extends BridgeConfig {

	public MQTTConfig keepAliveSeconds(int seconds);
	public MQTTConfig cleanSession(boolean clean);
	public MQTTConfig authentication(CharSequence user, CharSequence pass);
	public MQTTConfig will(boolean retrain, int qos, CharSequence topic, MQTTWritable write );
	
}
