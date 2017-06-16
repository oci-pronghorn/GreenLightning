package com.ociweb.gl.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.MsgRuntime;
import com.ociweb.gl.api.HTTPFieldReader;
import com.ociweb.gl.api.HTTPRequestReader;
import com.ociweb.gl.api.NetResponseTemplate;
import com.ociweb.gl.api.NetResponseWriter;
import com.ociweb.gl.api.RestListener;
import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;
import com.ociweb.pronghorn.network.config.HTTPHeaderDefaults;
import com.ociweb.pronghorn.util.Appendables;
import com.ociweb.pronghorn.util.math.Decimal;

public class MathUnit implements RestListener {

	private final Logger logger = LoggerFactory.getLogger(MathUnit.class);
	
	private final GreenCommandChannel<?> cc;
	private String lastCookie;
	private final byte[] fieldA = "a".getBytes();
	private final byte[] fieldB = "b".getBytes();
	
	private final NetResponseTemplate<HTTPFieldReader> template;

	public MathUnit(final MsgRuntime runtime) {

		this.cc = runtime.newCommandChannel(GreenCommandChannel.NET_RESPONDER);
		
		template = new NetResponseTemplate<HTTPFieldReader>()
				     .add("{\"x\":").add((w,s)->{s.getText(fieldA, w);})
				     .add(",\"y\":").add((w,s)->{s.getText(fieldB, w);})
				     .add(",\"groovySum\":").add((w,s)->{				    	 
				    	 Decimal.sum(
				    			 s.getDecimalMantissaDirect(fieldA),
				    			 s.getDecimalExponentDirect(fieldA), 
				    			 s.getDecimalMantissaDirect(fieldB),
				    			 s.getDecimalExponentDirect(fieldB),
				    			 (m,e)->{Appendables.appendDecimalValue(w, m, e);});				    	
				     })
				     .add("}");		
	}
	
	
	
	@Override
	public boolean restRequest(final HTTPRequestReader request) {
		
		final StringBuilder cookieValue = new StringBuilder();
		request.openHeaderData(HTTPHeaderDefaults.COOKIE.rootBytes(),(c)->{
			
			c.readUTF(cookieValue);
			lastCookie = cookieValue.toString();
			//System.out.println("cookie from browser: "+cookieValue);
		});
				
		return cc.publishHTTPResponse(request.getConnectionId(), request.getSequenceCode(), 200, END_OF_RESPONSE,
				                   HTTPContentTypeDefaults.JSON, (outputStream) -> {
										
										template.render(((NetResponseWriter)outputStream), request);
										((NetResponseWriter)outputStream).close();
													
									});		

	}

	public String getLastCookie() {
		return lastCookie;
	}	

}
