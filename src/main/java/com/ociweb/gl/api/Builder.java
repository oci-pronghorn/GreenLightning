package com.ociweb.gl.api;

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

	void parallelism(int parallel);
	
	int defineRoute(CharSequence route, byte[] ... headers);
	int registerRoute(CharSequence route, byte[] ... headers);
      
	void enableServer(boolean isTLS, boolean isLarge, String bindHost, int bindPort);	
	void enableServer(boolean isTLS, int bindPort);
	void enableServer(int bindPort);
	void enableServer(int bindPort, String defaultPath);
	void enableServer(boolean isTLS, boolean isLarge, String bindHost, int bindPort, String defaultPath);	
	void enableServer(boolean isTLS, int bindPort, String defaultPath);
	
	
	String enableTelemetry();
	
	void setDefaultRate(long ns);
	
	long fieldId(int routeId, byte[] fieldName);
	
	/*
	 * The default in-flight messages is 10
	 * The default maximum messageLength is 4K
	 */
	MQTTBridge useMQTT(CharSequence host, int port, CharSequence clientId);
	
	MQTTBridge useMQTT(CharSequence host, CharSequence clientId);
	
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
		
	
	void privateTopics(String ... topic);

	
	Builder useNetClient();
	Builder useInsecureNetClient();

}
