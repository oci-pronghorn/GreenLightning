package com.ociweb.gl.api;

import com.ociweb.json.JSONExtractorCompleted;
import com.ociweb.pronghorn.network.TLSCertificates;
import com.ociweb.pronghorn.network.http.CompositePath;

/**
 * Base interface for an IoT device's hardware.
 * <p>
 * This interface is most commonly used in conjunction with a call
 * to {@link MsgApp#declareConfiguration(Builder)} in order for
 * a maker's code to declare any hardware connections and resources
 * that it makes use of.
 *
 * @author Nathan Tippy
 */
public interface Builder extends ArgumentProvider {


    /**
     * Initializes the hardware state machine with the given {@link Enum} state.
     *
     * @param state Initial state to use.
     *
     * @return A reference to this hardware instance.
     */
    <E extends Enum<E>> Builder startStateMachineWith(E state);

    /**
     * Sets the trigger rate of events on this hardware.
     *
     * @param rateInMS Rate in milliseconds to trigger events.
     *
     * @return A reference to this hardware instance.
     */
    Builder setTimerPulseRate(long rateInMS);

    /**
     * Sets the trigger rate of events on this hardware.
     *
     * @param trigger {@link TimeTrigger} to use for controlling trigger rate.
     *
     * @return A reference to this hardware instance.
     */
    Builder setTimerPulseRate(TimeTrigger trigger);

    void limitThreads(int threadLimit);
    
    void limitThreads();

    int parallelTracks();
	void parallelTracks(int parallel);
	
	@Deprecated
	int defineRoute(CharSequence route, byte[] ... headers);
	@Deprecated
	int defineRoute(CharSequence route, JSONExtractorCompleted extractor, byte[] ... headers);

	CompositePath defineRoute(JSONExtractorCompleted extractor, byte[] ... headers);
	CompositePath defineRoute(byte[] ... headers);
	
	HTTPServerConfig useHTTP1xServer(int bindPort);

	TelemetryConfig enableTelemetry();
	TelemetryConfig enableTelemetry(int port);
	TelemetryConfig enableTelemetry(String host);
	TelemetryConfig enableTelemetry(String host, int port);
	
	void setDefaultRate(long ns);
	
	long fieldId(int routeId, byte[] fieldName);
	
	/*
	 * The default in-flight messages is 10
	 * The default maximum messageLength is 4K
	 */
	MQTTBridge useMQTT(CharSequence host, int port, CharSequence clientId);
	
	/*
	 * The maximum in-flight messages must be <= 32K
	 * The maximum messageLength must be <= 256M
	 */
	MQTTBridge useMQTT(CharSequence host, int port, CharSequence clientIdint, int maxInFlight);

	/*
	 * The maximum in-flight messages must be <= 32K
	 * The maximum messageLength must be <= 256M
	 */
	MQTTBridge useMQTT(CharSequence host, int port, CharSequence clientId, int maxInFlight, int maxMessageLength);
		
	//this will disable normal pub sub usages unless flagged.
	void definePrivateTopic(String topic, String source, String target);
	void definePrivateTopic(String topic, String source, String ... targets);
	void definePrivateTopic(int queueLength, int maxMessageSize, String topic, String source, String target);
	void definePrivateTopic(int queueLength, int maxMessageSize, String topic, String source, String ... targets);
	void usePrivateTopicsExclusively();
	void defineUnScopedTopic(String topic);
	
	void enableDynamicTopicPublish(String id);//without this any private topic above will not support dynamic routing
	void enableDynamicTopicSubscription(String id);
	


	HTTPClientConfig useNetClient();
	HTTPClientConfig useNetClient(TLSCertificates certificates);
	HTTPClientConfig useInsecureNetClient();

}
