package com.ociweb.gl.example.parallel;

import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPRequestReader;
import com.ociweb.gl.api.RestListener;

public class RestConsumer implements RestListener {
	
	private GreenCommandChannel cmd2;	
	public RestConsumer(GreenRuntime runtime) {		
		cmd2 = runtime.newCommandChannel();		
		cmd2.ensureDynamicMessaging();		
	}


	@Override
	public boolean restRequest(HTTPRequestReader request) {		
		cmd2.publishTopic("/send/200",(w)->{
			w.writePackedLong(request.getConnectionId());
			w.writePackedLong(request.getSequenceCode());			
		}); 
	//	cmd2.publishTopic("/test/gobal");//tell the watcher its good
		return true;
	}

}
