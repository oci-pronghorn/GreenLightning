package com.ociweb.gl.api;

import com.ociweb.gl.impl.BuilderImpl;

public interface GreenApp extends MsgApp<Builder, MsgRuntime<BuilderImpl, ListenerFilter>> {
	
	public static final int ALL = GreenCommandChannel.DYNAMIC_MESSAGING | 
            GreenCommandChannel.NET_REQUESTER | 
            GreenCommandChannel.NET_RESPONDER;

	public static final int DYNAMIC_MESSAGING = GreenCommandChannel.DYNAMIC_MESSAGING;
	public static final int NET_REQUESTER = GreenCommandChannel.NET_REQUESTER;
	public static final int NET_RESPONDER = GreenCommandChannel.NET_RESPONDER;

}
