package com.ociweb.gl.api;

public interface GreenApp extends MsgApp<Builder, GreenRuntime> {
	
	public static final int ALL = GreenCommandChannel.DYNAMIC_MESSAGING | 
            GreenCommandChannel.NET_REQUESTER | 
            GreenCommandChannel.NET_RESPONDER;

	public static final int DYNAMIC_MESSAGING = GreenCommandChannel.DYNAMIC_MESSAGING;
	public static final int NET_REQUESTER = GreenCommandChannel.NET_REQUESTER;
	public static final int NET_RESPONDER = GreenCommandChannel.NET_RESPONDER;

}
