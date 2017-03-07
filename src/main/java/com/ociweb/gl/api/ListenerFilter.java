package com.ociweb.gl.api;

public interface ListenerFilter {

		
	/**
	 * Add subscription to this topic to this listener at startup.
	 * @param topic
	 */
	ListenerFilter addSubscription(CharSequence topic); 
	
	/**
	 * For StateChangeListener reduce notifications.
	 * @param states
	 */
	<E extends Enum<E>> ListenerFilter includeStateChangeTo(E ... states); 

	/**
	 * For StateChangeListener reduce notifications.
	 * @param states
	 */
	<E extends Enum<E>> ListenerFilter excludeStateChangeTo(E ... states); 

	/**
	 * For StateChangeListener reduce notifications.
	 * @param states
	 */
	<E extends Enum<E>> ListenerFilter includeStateChangeFrom(E ... states); 
	
	/**
	 * For StateChangeListener reduce notifications.
	 * @param states
	 */
	<E extends Enum<E>> ListenerFilter includeStateChangeToAndFrom(E ... states); 
		
	/**
	 * For StateChangeListener reduce notifications.
	 * @param states
	 */
	<E extends Enum<E>> ListenerFilter excludeStateChangeFrom(E ... states);

	
	ListenerFilter includeRoutes(int ... routeIds);
	
	
	ListenerFilter excludeRoutes(int ... routeIds);
	
	
	
}
