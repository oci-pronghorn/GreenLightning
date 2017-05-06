package com.ociweb.gl.api;

public interface Commandable {

	void publishGo(int count, int pipeIdx);
	int subPipeIdx();
	
}
