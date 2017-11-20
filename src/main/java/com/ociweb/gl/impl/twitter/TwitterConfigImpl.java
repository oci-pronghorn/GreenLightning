package com.ociweb.gl.impl.twitter;

import com.ociweb.gl.api.MsgRuntime;
import com.ociweb.gl.api.TwitterBridge;
import com.ociweb.gl.impl.BridgeConfigImpl;
import com.ociweb.gl.impl.stage.EgressConverter;
import com.ociweb.gl.impl.stage.IngressConverter;

public class TwitterConfigImpl extends BridgeConfigImpl<TwitterConfigTransmission,TwitterConfigSubscription> implements TwitterBridge {

	//TODO: twitter bridge allows for setting of all user credentials.
	
	@Override
	public long addSubscription(CharSequence internalTopic, CharSequence externalTopic) {
		// TODO this external twitter must come into this topic.
		//     is the external a filter query or is it a user??
		//         /twitter/user
		//         /twitter/query/<QUERY>
		return 0;
	}

	@Override
	public long addSubscription(CharSequence internalTopic, CharSequence externalTopic, IngressConverter converter) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public long addTransmission(MsgRuntime<?, ?> msgRuntime, CharSequence internalTopic, CharSequence externalTopic) {
		// TODO send this internal topic out to twitter.
		//      can post to /twitter/user, name is not important?		
		
		
		return 0;
	}

	@Override
	public long addTransmission(MsgRuntime<?, ?> msgRuntime, CharSequence internalTopic, CharSequence externalTopic,
			EgressConverter converter) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public TwitterConfigTransmission transmissionConfigurator(long id) {
		
		// TODO NOT SURE WHAT FEATURES TO ADD..
		
		return null;
	}

	@Override
	public TwitterConfigSubscription subscriptionConfigurator(long id) {
		// TODO additional word filters on input?
		
		return null;
	}

	@Override
	public void finalizeDeclareConnections(MsgRuntime<?, ?> msgRuntime) {
		// TODO build Ingress and Egress stages
		
	}

}
