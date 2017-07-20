package com.ociweb.gl.api;

public interface RouteFilter {
	
	ListenerFilter includeRoutes(int ... routeIds);
	
	ListenerFilter includeAllRoutes();
	
}
