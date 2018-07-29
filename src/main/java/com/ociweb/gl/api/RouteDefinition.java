package com.ociweb.gl.api;

import com.ociweb.pronghorn.network.http.CompositeRoute;

public interface RouteDefinition {

	ExtractedJSONFields parseJSON();
			
	CompositeRoute path(CharSequence path);
	
}
