package com.ociweb.gl.api;

import com.ociweb.gl.impl.stage.CallableMethod;
import com.ociweb.gl.impl.stage.CallableRestRequestReader;
import com.ociweb.gl.impl.stage.CallableStaticMethod;
import com.ociweb.gl.impl.stage.CallableStaticRestRequestReader;

public interface ListenerFilter extends RouteFilter<ListenerFilter> {


	<T extends Behavior> ListenerFilter addSubscription(CharSequence topic, CallableStaticMethod<T> method);
	
	ListenerFilter addSubscription(CharSequence topic, CallableMethod method);
		
	
	<T extends Behavior> ListenerFilter includeRoute(int routeId, CallableStaticRestRequestReader<T> callable);
		
	ListenerFilter includeRoute(int routeId, CallableRestRequestReader callable);

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

	
	int getId();	
	
	
}
