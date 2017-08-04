package com.ociweb.gl.api;

import com.ociweb.gl.impl.stage.EgressConverter;
import com.ociweb.gl.impl.stage.IngressConverter;

public interface BridgeConfig<T,S> {

	long addSubscription(CharSequence topic);
	long addSubscription(CharSequence internalTopic, CharSequence externalTopic);
	long addSubscription(CharSequence internalTopic, CharSequence externalTopic, IngressConverter converter);
	
	long addTransmission(MsgRuntime<?,?> msgRuntime, CharSequence topic);
	long addTransmission(MsgRuntime<?,?> msgRuntime, CharSequence internalTopic, CharSequence externalTopic);
	long addTransmission(MsgRuntime<?,?> msgRuntime, CharSequence internalTopic, CharSequence externalTopic, EgressConverter converter);
	
	T transmissionConfigurator(long id);
	S subscriptionConfigurator(long id);

}
