package com.ociweb.gl.api;

import com.ociweb.gl.impl.mqtt.MQTTConfigSubscription;
import com.ociweb.gl.impl.mqtt.MQTTConfigTransmission;

public interface MQTTBridge extends BridgeConfig<MQTTConfigTransmission, MQTTConfigSubscription> {

	int defaultPort = 1883;
	int tlsPort = 8883;

	public MQTTBridge keepAliveSeconds(int seconds);
	public MQTTBridge cleanSession(boolean clean);
	public MQTTBridge authentication(CharSequence user, CharSequence pass);
	public MQTTBridge connectionWill(MQTTConnectionWill will);
	public MQTTBridge subscriptionQoS(MQTTQoS qos);
	public MQTTBridge transmissionOoS(MQTTQoS qos);
	public MQTTBridge transmissionRetain(boolean value);
	
}
