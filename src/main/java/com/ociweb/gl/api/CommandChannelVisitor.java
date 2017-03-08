package com.ociweb.gl.api;

public interface CommandChannelVisitor {

	public void visit(CommandChannel cmdChnl);

	public void visit(ListenerConfig cnfg);
	
}
