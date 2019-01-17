package com.ociweb.gl.example.parallel;

import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPRequestReader;
import com.ociweb.gl.api.HTTPResponseService;
import com.ociweb.gl.api.MsgCommandChannel;
import com.ociweb.gl.api.PubSubFixedTopicService;
import com.ociweb.gl.api.PubSubService;
import com.ociweb.gl.api.RestListener;
import com.ociweb.gl.api.Writable;
import com.ociweb.pronghorn.pipe.ChannelWriter;

public class RestConsumer implements RestListener {
	
	private static final byte[] v2 = "st".getBytes();
	private static final byte[] v1 = "value".getBytes();
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
	private PubSubFixedTopicService messageService;
	private HTTPResponseService responseService;
	public RestConsumer(GreenRuntime runtime, long fieldA, long fieldB,
			Object objectA,
			Object objectB,
			Object valueObj) {		
		this.cmd2 = runtime.newCommandChannel();		
		this.messageService = this.cmd2.newPubSubService("/send/200",64,400);
		this.responseService = this.cmd2.newHTTPResponseService(8,400);
		this.fieldA = fieldA;
		this.fieldB = fieldB;	
		this.valueObject = valueObj;
				
	}


	@Override
	public boolean restRequest(final HTTPRequestReader request) {
		
		if (!( request.isVerbPost() || request.isVerbGet() )) {
			responseService.publishHTTPResponse(request, 404);
		}

		assert(request.structured().isEqual(fieldA, v1));
		
		assert(request.structured().isEqual(valueObject, v2)) : "found "+request.structured().readText(valueObject);
				
		int b = request.structured().readInt(fieldB);
		if (b!=123) {
			throw new UnsupportedOperationException();
		}
		
		requestW = request;
		return messageService.publishTopic(w);

		
	//	cmd2.publishTopic("/test/gobal");//tell the watcher its good

	}

}
