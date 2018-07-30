package com.ociweb.gl.api;

import com.ociweb.pronghorn.network.http.CompositeRoute;

public interface ExtractedJSONFieldsForRoute extends ExtractedJSONFields<ExtractedJSONFieldsForRoute> {

    CompositeRoute path(CharSequence path);
}
