package com.ociweb.gl.api;

public class MQTTConnectionWill {
	public boolean latWillRetain = false;
	public MQTTQoS lastWillQoS = MQTTQoS.atMostOnce;
	public CharSequence lastWillTopic = null;
	public Writable lastWillPayload = null;
	public CharSequence connectFeedbackTopic = null;
}
