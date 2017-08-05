package com.ociweb.gl.impl;

public interface MQTTConfigTransmission {

	MQTTConfigTransmission setQoS(MQTTQOS qos);
	MQTTConfigTransmission setRetain(boolean retain);
}
