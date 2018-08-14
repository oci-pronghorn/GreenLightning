package com.ociweb.gl.api;

public enum MQTTQoS {
	atMostOnce(0),
	atLeastOnce(1),
	exactlyOnce(2);

	static final int failedSubscription = 0x80;

	private final int specification;

	MQTTQoS(int specification) {
		this.specification = specification;
	}

	public int getSpecification() {
		return this.specification;
	}
}
