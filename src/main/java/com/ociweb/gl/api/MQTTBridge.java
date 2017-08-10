package com.ociweb.gl.api;

import com.ociweb.gl.impl.MQTTQOS;
import com.ociweb.gl.impl.mqtt.MQTTConfigSubscription;
import com.ociweb.gl.impl.mqtt.MQTTConfigTransmission;

public interface MQTTBridge extends BridgeConfig<MQTTConfigTransmission, MQTTConfigSubscription> {

	public MQTTBridge keepAliveSeconds(int seconds);
	public MQTTBridge cleanSession(boolean clean);
	public MQTTBridge authentication(CharSequence user, CharSequence pass);
	public MQTTBridge will(boolean retrain, int qos, CharSequence topic, Writable write );
	public MQTTBridge subscriptionQoS(MQTTQOS qos);
	public MQTTBridge transmissionOoS(MQTTQOS qos);
	public MQTTBridge transmissionRetain(boolean value);
	
}
