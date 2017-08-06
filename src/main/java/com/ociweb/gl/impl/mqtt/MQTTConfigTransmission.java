package com.ociweb.gl.impl.mqtt;

public interface MQTTConfigTransmission {

	public MQTTConfigTransmission setQoS(int qos);
	public MQTTConfigTransmission setRetain(boolean retain);
}
