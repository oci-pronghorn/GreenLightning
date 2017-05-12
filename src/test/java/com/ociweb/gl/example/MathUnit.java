package com.ociweb.gl.example;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.gl.api.CommandChannel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPFieldReader;
import com.ociweb.gl.api.HeaderReader;
import com.ociweb.gl.api.NetResponseTemplate;
import com.ociweb.gl.api.NetResponseWriter;
import com.ociweb.gl.api.RestListener;
import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;
import com.ociweb.pronghorn.network.config.HTTPHeaderDefaults;
import com.ociweb.pronghorn.util.Appendables;
import com.ociweb.pronghorn.util.math.Decimal;

public class MathUnit implements RestListener {

	private final Logger logger = LoggerFactory.getLogger(MathUnit.class);
	
	private final CommandChannel cc;
	private String lastCookie;
	
	private final NetResponseTemplate<HTTPFieldReader> template;

	public MathUnit(final GreenRuntime runtime) {

		this.cc = runtime.newCommandChannel(/*CommandChannel.DYNAMIC_MESSAGING |*/ CommandChannel.NET_RESPONDER);
       
		//TODO: by adding exclusive topics we can communnicated pont to point 
		//runtime.setExclusiveTopics(cc,"myTopic","myOtherTopic");
		
		//example response UTF-8 encoded
		//{"x":9,"y":17,"groovySum":26}
		
		final byte[] fieldA = "a".getBytes();
		final byte[] fieldB = "b".getBytes();
		
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
	public boolean restRequest(HTTPFieldReader request) {
		
		final StringBuilder cookieValue = new StringBuilder();
		Optional<HeaderReader> cookieReader = request.openHeaderData(HTTPHeaderDefaults.COOKIE.rootBytes());
		cookieReader.ifPresent((c)->{
			
			c.readUTF(cookieValue);
			lastCookie = cookieValue.toString();
			//System.out.println("cookie from browser: "+cookieValue);
		});
				
		
		Optional<NetResponseWriter> writer = cc.openHTTPResponse(request.getConnectionId(), request.getSequenceCode(), 200, END_OF_RESPONSE, HTTPContentTypeDefaults.JSON); 
				
		writer.ifPresent( (outputStream) -> {
			
			template.render(outputStream, request);
			outputStream.close();
						
		});		

		return writer.isPresent(); //if false is returned then this method will be called again later with the same inputs.

	}

	public String getLastCookie() {
		return lastCookie;
	}	

}
