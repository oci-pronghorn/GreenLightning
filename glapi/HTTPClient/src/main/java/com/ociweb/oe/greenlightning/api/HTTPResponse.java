package com.ociweb.oe.greenlightning.api;

import com.ociweb.gl.api.HTTPResponseListener;
import com.ociweb.gl.api.HTTPResponseReader;
import com.ociweb.gl.api.Payloadable;
import com.ociweb.pronghorn.pipe.ChannelReader;
import com.ociweb.pronghorn.util.AppendableProxy;
import com.ociweb.pronghorn.util.Appendables;

public class HTTPResponse implements HTTPResponseListener {

	
	
	private AppendableProxy console;

	public HTTPResponse(Appendable console) {
		this.console = Appendables.wrap(console);
	}
	
	@Override
	public boolean responseHTTP(HTTPResponseReader reader) {
		
		Appendables.appendValue(console, " status:",  reader.statusCode(),"\n");
		
		console.append("   type:").append(reader.contentType().toString()).append("\n");
	
		
		Payloadable payload = new Payloadable() {
			@Override
			public void read(ChannelReader reader) {
				if (reader.available()<1) {
					//error
					return;
				}
				int age = reader.structured().readInt(Fields.AGE);
				String name = reader.structured().readText(Fields.NAME);
				
				Appendables.appendValue(console.append(name).append(" "),age).append("\n");
				
			}
		};
		boolean hadAbody = reader.openPayloadData(payload );

		
		return true;
	}

}
