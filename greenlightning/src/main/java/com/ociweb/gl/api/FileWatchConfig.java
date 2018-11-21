package com.ociweb.gl.api;

public interface FileWatchConfig {
	
	FileWatchConfig filePattern(String route);
	int routeId(Object associatedObject); //end of details
	
	//extend for JSON field def...
	//extend for CSV field def...
	
}
