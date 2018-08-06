package com.ociweb.gl.api;

import com.ociweb.json.JSONExtractorCompleted;
import com.ociweb.json.decode.JSONExtractor;
import com.ociweb.pronghorn.network.HTTPServerConfig;
import com.ociweb.pronghorn.network.TLSCertificates;
import com.ociweb.pronghorn.network.config.HTTPHeader;
import com.ociweb.pronghorn.network.http.CompositePath;
import com.ociweb.pronghorn.struct.StructBuilder;

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

    /**
	 * Used to limit the threads for telemetry
     */
    void limitThreads(int threadLimit);

	void limitThreads();

    /**
     *
     * @return number of parallel tracks declared for this builder
     */
    int parallelTracks();
	void parallelTracks(int parallel);

    /**
     * Registers an allowed URL path that server can receive requests on
     * @return Reference used when registering request listeners
     */
	@Deprecated
	CompositePath defineRoute(JSONExtractorCompleted extractor, HTTPHeader ... headers);
	//CompositePath defineRoute(HTTPHeader ... headers);
	RouteDefinition defineRoute(HTTPHeader ... headers);


    /**
     * Creates a new struct builder that allows you to define named values on a channel
     * @return the new struct built
     */
	JSONExtractor defineJSONSDecoder();
	JSONExtractor defineJSONSDecoder(boolean writeDot);

	StructBuilder defineStruct();

    /**
     * Create a new struct which is a replica of the one passed in for extension
     * @param template struct to be passed in
     * @return the replica struct
     */
	StructBuilder extendStruct(StructBuilder template);

    /**
     * Create a web server on the port passed in
     * @param bindPort port to be passed to web server
     * @return Created web server
     */
	HTTPServerConfig useHTTP1xServer(int bindPort);

    /**
     * Enables the dev ops view of the running app
     */
	TelemetryConfig enableTelemetry();

    /**
     * Enables the dev ops view of the running app using specified port
     * @param port port to pass to use for Telemetry
     */
	TelemetryConfig enableTelemetry(int port);

    /**
     * Enables the dev ops view of the running app through specified host
     * @param host host to pass along to Telemetry
     */
	TelemetryConfig enableTelemetry(String host);

    /**
     * Enables the dev ops view of the running app through specified host using specified port
     * @param host host to pass along to Telemetry
     * @param port port to pass to use for Telemetry
     */
	TelemetryConfig enableTelemetry(String host, int port);
	
	
	void useInsecureSerialStores(int instances, int largestBlock);
	void useSerialStores(int instances, int largestBlock, String passphrase);
	void useSerialStores(int instances, int largestBlock, byte[] passphrase);

    /**
     * The pulse interval for work chasing
     * @param ns default rate in nanoseconds
     */
	void setDefaultRate(long ns);
	
	/*
	 * The default in-flight messages is 10
	 * The default maximum messageLength is 4K
	 */

    /**
     * Activates the MQTT client
     * @param host the host used by the MQTT client
     * @param port the port used by the MQTT client
     * @param clientId the client id used by the MQTT client
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

    /**
     * Defined data path route directly from one behavior to another
     * @param topic topic to be defined
     * @param source source of route
     * @param target target behavior
     */
	//this will disable normal pub sub usages unless flagged.
	void definePrivateTopic(String topic, String source, String target);
	void definePrivateTopic(String topic, String source, String ... targets);
	void definePrivateTopic(int queueLength, int maxMessageSize, String topic, String source, String target);
	void definePrivateTopic(int queueLength, int maxMessageSize, String topic, String source, String ... targets);
	
	//this blocks automatic private topics as needed.
	void definePublicTopics(String ... topics);
	
    /**
     * Sets isAllPrivateTopics to true
     */
	void usePrivateTopicsExclusively();

    /**
     * Any topics broadcast from a private behavior that you want to go to pubsub
     * @param topic specified topic
     */
	void defineUnScopedTopic(String topic);
	
	void enableDynamicTopicPublish(String id);//without this any private topic above will not support dynamic routing
	void enableDynamicTopicSubscription(String id);


    /**
     * Activates HTTPS client services
     */
	HTTPClientConfig useNetClient();

    /**
     * Activates HTTPS client services using specified certificate
     * @param certificates certificates to use for HTTPS client
     */
	HTTPClientConfig useNetClient(TLSCertificates certificates);

	HTTPClientConfig useInsecureNetClient();

    /**
     * Used to set maximum expected latency
     * @param ns long value in nanoseconds for latency
     */
	void setGlobalSLALatencyNS(long ns);

    /**
     * Get the field identifier from a route
     * @param aRouteId route id to search for
     * @param name name to search for
     */
	long lookupFieldByName(int aRouteId, String name);
	long lookupFieldByIdentity(int aRouteId, Object obj);

}
