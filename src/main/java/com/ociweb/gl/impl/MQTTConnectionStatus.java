package com.ociweb.gl.impl;

public enum MQTTConnectionStatus {
	connected(0),
	unacceptedProtocol(1),
	invalidIdentifier(2),
	serviceUnavailable(3),
	invalidCredentials(4),
	notAuthorized(5),
	other(255);

	private final int specification;

	MQTTConnectionStatus(int specification) {
		this.specification = specification;
	}

	public int getSpecification() {
		return this.specification;
	}

	public static MQTTConnectionStatus fromSpecification(int specification) {
		switch(specification) {
			case 0:
				return MQTTConnectionStatus.connected;
			case 1:
				return MQTTConnectionStatus.unacceptedProtocol;
			case 2:
				return MQTTConnectionStatus.invalidIdentifier;
			case 3:
				return MQTTConnectionStatus.serviceUnavailable;
			case 4:
				return MQTTConnectionStatus.invalidCredentials;
			case 5:
				return MQTTConnectionStatus.notAuthorized;
			default:
				return MQTTConnectionStatus.other;
		}
	}

	@Override
	public String toString() {
		switch(getSpecification()) {
			case 1:
				return "The Server does not support the level of the MQTT protocol requested by the Client";
			case 2:
				return "The Client identifier is correct UTF-8 but not allowed by the Server";
			case 3:
				return "The Network Connection has been made but the MQTT service is unavailable";
			case 4:
				return "The data in the user name or password is malformed";
			case 5:
				return "The Client is not authorized to connect";
			default:
				return "Unknown connection error";
		}
	}
}
