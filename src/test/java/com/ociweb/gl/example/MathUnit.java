package com.ociweb.gl.example;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.gl.api.CommandChannel;
import com.ociweb.gl.api.FieldReader;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPFieldReader;
import com.ociweb.gl.api.HeaderReader;
import com.ociweb.gl.api.ListenerConfig;
import com.ociweb.gl.api.NetResponseWriter;
import com.ociweb.gl.api.PayloadWriter;
import com.ociweb.gl.api.RestListener;
import com.ociweb.gl.impl.PayloadReader;
import com.ociweb.pronghorn.network.ServerCoordinator;
import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;
import com.ociweb.pronghorn.network.config.HTTPHeaderDefaults;
import com.ociweb.pronghorn.network.config.HTTPVerb;
import com.ociweb.pronghorn.network.config.HTTPVerbDefaults;
import com.ociweb.pronghorn.network.schema.HTTPRequestSchema;
import com.ociweb.pronghorn.pipe.DataInputBlobReader;
import com.ociweb.pronghorn.pipe.DataOutputBlobWriter;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.util.Appendables;
import com.ociweb.pronghorn.util.math.Decimal;

public class MathUnit implements RestListener {

	private final Logger logger = LoggerFactory.getLogger(MathUnit.class);
	
	private final CommandChannel cc;
	private String lastCookie;
	
	//example response UTF-8 encoded
	//{"x":9,"y":17,"groovySum":26}
	private final byte[] part1 = "{\"x\":".getBytes();
	private final byte[] part2 =          ",\"y\":".getBytes();
	private final byte[] part3 =                    ",\"groovySum\":".getBytes();
	private final byte[] part4 =                                      "}".getBytes();
	
	//these member vars can be used because the stage will only use 1 thread for calling process method.
	private final StringBuilder a = new StringBuilder();
	private final StringBuilder b = new StringBuilder();
	private final StringBuilder c = new StringBuilder();
	
	public MathUnit(final GreenRuntime runtime) {

		
		this.cc = runtime.newCommandChannel(/*CommandChannel.DYNAMIC_MESSAGING |*/ CommandChannel.NET_RESPONDER);
       
		//TODO: by adding exclusive topics we can communnicated pont to point 
		//runtime.setExclusiveTopics(cc,"myTopic","myOtherTopic");
		
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

		
		
				
		double a1 = request.getDouble("a".getBytes());
		double b1 = request.getDouble("b".getBytes());
		
		c.setLength(0);
		c.append(a1+b1);
	
		
		//optional but without it we must chunk, if it does not match lengh we will throw..
		int length = part1.length+a.length()+
				 	 part2.length+b.length()+
					 part3.length+c.length()+
					 part4.length;
	
		//3 masks,  end data,  close connection, upgrade

		int maxContentLength = cc.maxHTTPContentLength; //biggest block we can publish at a time.
		assert(length<maxContentLength); //if we needed to send more data we would not set the end data flag
		
		//| ServerCoordinator.END_RESPONSE_MASK;
		int context = END_OF_RESPONSE; //if we choose we can or this with CLOSE_CONNECTION or not and leave the connection open for more calls.
				
		int statusCode = 200;
        
		Optional<NetResponseWriter> writer = cc.openHTTPResponse(request.getConnectionId(), request.getSequenceCode(), statusCode, context, HTTPContentTypeDefaults.JSON, length); 
				
		writer.ifPresent( (outputStream) -> {
			
			outputStream.write(part1);
			
			outputStream.writeUTF8Text(a);
			
			outputStream.write(part2);
			
			outputStream.writeUTF8Text(b);
			
			outputStream.write(part3);
			
			outputStream.writeUTF8Text(c);
			
			outputStream.write(part4);
			
			outputStream.close();
						
		});		
		
//////////////////////
//example code for sending response with follow on parts
//output = cc.openHTTPResponseContinuation(connectionId, sequenceNo, context);
//////////////////////

		return writer.isPresent(); //if false is returned then this method will be called again later with the same inputs.

	}



	public String getLastCookie() {
		return lastCookie;
	}	

}
