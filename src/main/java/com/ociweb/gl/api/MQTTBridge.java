package com.ociweb.gl.api;

import com.ociweb.gl.impl.mqtt.MQTTConfigSubscription;
import com.ociweb.gl.impl.mqtt.MQTTConfigTransmission;
import com.ociweb.pronghorn.network.TLSCertificates;

public interface MQTTBridge extends BridgeConfig<MQTTConfigTransmission, MQTTConfigSubscription> {

	int defaultPort = 1883;
	int tlsPort = 8883;

	MQTTBridge keepAliveSeconds(int seconds);
	MQTTBridge cleanSession(boolean clean);
	MQTTBridge useTLS();
	MQTTBridge useTLS(TLSCertificates certificates);
	MQTTBridge authentication(CharSequence user, CharSequence pass);
	MQTTBridge authentication(CharSequence user, CharSequence pass, TLSCertificates certificates);
	MQTTBridge lastWill(CharSequence topic, boolean retain, MQTTQoS qos, Writable payload);
	MQTTBridge connectionFeedbackTopic(CharSequence connectFeedbackTopic);
	MQTTBridge subscriptionQoS(MQTTQoS qos);
	MQTTBridge transmissionOoS(MQTTQoS qos);
	MQTTBridge transmissionRetain(boolean value);
}
