package com.ociweb.gl.api;

public interface GreenApp extends MsgApp<Builder, GreenRuntime> {
	
	public static final int ALL = MsgCommandChannel.DYNAMIC_MESSAGING | 
            MsgCommandChannel.NET_REQUESTER | 
            MsgCommandChannel.NET_RESPONDER;

	public static final int DYNAMIC_MESSAGING = MsgCommandChannel.DYNAMIC_MESSAGING;
	public static final int NET_REQUESTER = MsgCommandChannel.NET_REQUESTER;
	public static final int NET_RESPONDER = MsgCommandChannel.NET_RESPONDER;
	public static final int USE_DELAY = MsgCommandChannel.USE_DELAY;
}
