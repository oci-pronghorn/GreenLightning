package com.ociweb.gl.impl;

public enum MQTTQOS {
	atMostOnce(0),
	atLeastOnce(1),
	exactlyOnce(2);

	private final int specification;

	MQTTQOS(int specification) {
		this.specification = specification;
	}

	public int getSpecification() {
		return this.specification;
	}
}
