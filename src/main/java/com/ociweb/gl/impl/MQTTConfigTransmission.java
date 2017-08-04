package com.ociweb.gl.impl;

public interface MQTTConfigTransmission {

	public MQTTConfigTransmission setQoS(int qos);
	public MQTTConfigTransmission setRetain(boolean retain);
}
