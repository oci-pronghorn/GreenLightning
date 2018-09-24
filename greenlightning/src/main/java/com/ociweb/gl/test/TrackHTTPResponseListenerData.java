package com.ociweb.gl.test;

public class TrackHTTPResponseListenerData {
	public long waitingCountDown;
	public long callRequestInstanceCounter;
	public long responsesReceived;
	public boolean lastResponseOk;

	public TrackHTTPResponseListenerData() {
		this.lastResponseOk = true;
	}
}