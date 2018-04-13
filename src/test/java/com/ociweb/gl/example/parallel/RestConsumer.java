package com.ociweb.gl.example.parallel;

import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPRequestReader;
import com.ociweb.gl.api.HTTPResponseService;
import com.ociweb.gl.api.MsgCommandChannel;
import com.ociweb.gl.api.PubSubService;
import com.ociweb.gl.api.RestListener;
import com.ociweb.gl.api.Writable;
import com.ociweb.pronghorn.pipe.ChannelWriter;

public class RestConsumer implements RestListener {
	
	private GreenCommandChannel cmd2;	
	private HTTPRequestReader requestW;
	private final long fieldA;
	private final long fieldB;
	private final Object valueObject;
	
	private Writable w = new Writable() {

		@Override
		public void write(ChannelWriter writer) {
			writer.writePackedLong(requestW.getConnectionId());
			writer.writePackedLong(requestW.getSequenceCode());	
			long track = 0;//unknown
			writer.writePackedLong(track);
		}
		
	};
	private PubSubService messageService;
	private HTTPResponseService responseService;
	public RestConsumer(GreenRuntime runtime, long fieldA, long fieldB,
			Object objectA,
			Object objectB,
			Object valueObj) {		
		this.cmd2 = runtime.newCommandChannel();		
		this.messageService = this.cmd2.newPubSubService();
		this.responseService = this.cmd2.newHTTPResponseService();
		this.fieldA = fieldA;
		this.fieldB = fieldB;	
		this.valueObject = valueObj;
				
	}


	@Override
	public boolean restRequest(final HTTPRequestReader request) {
		
		if (!( request.isVerbPost() || request.isVerbGet() )) {
			responseService.publishHTTPResponse(request, 404);
		}
		
		String valueA = request.structured().readText(fieldA);
		assert(valueA.equals("value")) : "found "+valueA;

		assert(request.structured().isEqual(valueObject, "st".getBytes())) : "found "+request.structured().readText(valueObject);
				
		int b = request.structured().readInt(fieldB);
		if (b!=123) {
			throw new UnsupportedOperationException();
		}
		
		requestW = request;
		return messageService.publishTopic("/send/200", w);

		
	//	cmd2.publishTopic("/test/gobal");//tell the watcher its good

	}

}
