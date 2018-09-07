package com.mydomain.greenlightning.slipstream;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPRequestReader;
import com.ociweb.gl.api.HTTPResponseService;
import com.ociweb.gl.api.RestListener;
import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;

public class InternalService implements RestListener {

	private final HTTPResponseService responseService;

	public InternalService(GreenRuntime runtime) {
		responseService = runtime.newCommandChannel().newHTTPResponseService();		
	}
	
	@Override
	public boolean restRequest(HTTPRequestReader request) {
		
		return responseService.publishHTTPResponse(request, 
				                                   200, 
				                                   HTTPContentTypeDefaults.PLAIN,
				                                   (w)-> w.append("Hello World") );
		
	}

}
