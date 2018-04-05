package com.ociweb.gl.example.parallel;

import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPRequestReader;
import com.ociweb.gl.api.MsgCommandChannel;
import com.ociweb.gl.api.RestListener;
import com.ociweb.gl.api.Writable;
import com.ociweb.pronghorn.pipe.ChannelWriter;

public class RestConsumer implements RestListener {
	
	private GreenCommandChannel cmd2;	
	private HTTPRequestReader requestW;
	private final long fieldA;
	private final long fieldB;
	
	
	private Writable w = new Writable() {

		@Override
		public void write(ChannelWriter writer) {
			writer.writePackedLong(requestW.getConnectionId());
			writer.writePackedLong(requestW.getSequenceCode());	
			long track = 0;//unknown
			writer.writePackedLong(track);
		}
		
	};
	public RestConsumer(GreenRuntime runtime, long fieldA, long fieldB) {		
		this.cmd2 = runtime.newCommandChannel();		
		this.cmd2.ensureDynamicMessaging();
		this.cmd2.ensureHTTPServerResponse();
		this.fieldA = fieldA;
		this.fieldB = fieldB;		
				
	}


	@Override
	public boolean restRequest(final HTTPRequestReader request) {
		
		if (!( request.isVerbPost() || request.isVerbGet() )) {
			cmd2.publishHTTPResponse(request, 404);
		}
		
		int b = request.structured().readInt(fieldB);
		if (b!=123) {
			throw new UnsupportedOperationException();
		}
		
		requestW = request;
		return cmd2.publishTopic("/send/200", w);

		
	//	cmd2.publishTopic("/test/gobal");//tell the watcher its good

	}

}
