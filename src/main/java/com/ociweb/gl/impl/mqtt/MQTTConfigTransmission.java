package com.ociweb.gl.impl.mqtt;

import com.ociweb.gl.impl.MQTTQOS;

public interface MQTTConfigTransmission {

	public MQTTConfigTransmission setQoS(MQTTQOS qos);
	public MQTTConfigTransmission setRetain(boolean retain);
}
