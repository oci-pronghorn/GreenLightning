package com.ociweb.gl.impl.mqtt;

import com.ociweb.gl.api.MQTTQoS;

public interface MQTTConfigTransmission {

	public MQTTConfigTransmission setQoS(MQTTQoS qos);
	public MQTTConfigTransmission setRetain(boolean retain);
}
