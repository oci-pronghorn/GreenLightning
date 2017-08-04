package com.ociweb.gl.api;

import com.ociweb.gl.impl.MQTTConfigSubscription;
import com.ociweb.gl.impl.MQTTConfigTransmission;

public interface MQTTBridge extends BridgeConfig<MQTTConfigTransmission, MQTTConfigSubscription> {

	public MQTTBridge keepAliveSeconds(int seconds);
	public MQTTBridge cleanSession(boolean clean);
	public MQTTBridge authentication(CharSequence user, CharSequence pass);
	public MQTTBridge will(boolean retrain, int qos, CharSequence topic, MQTTWritable write );
	public MQTTBridge subscriptionQoS(int value);
	public MQTTBridge transmissionOoS(int value);
	public MQTTBridge transmissionRetain(boolean value);
	
}
