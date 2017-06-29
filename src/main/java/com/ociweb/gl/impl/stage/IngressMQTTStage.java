package com.ociweb.gl.impl.stage;

import com.ociweb.gl.impl.schema.IngressMessages;
import com.ociweb.pronghorn.network.schema.MQTTClientResponseSchema;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeReader;
import com.ociweb.pronghorn.stage.PronghornStage;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;

public class IngressMQTTStage extends PronghornStage {

	private final Pipe<MQTTClientResponseSchema> input;
	private final Pipe<IngressMessages> output;
	
	public IngressMQTTStage(GraphManager graphManager, Pipe<MQTTClientResponseSchema> input, Pipe<IngressMessages> output) {
		super(graphManager, input, output);
		this.input = input;
		this.output = output;
	}

	@Override
	public void run() {
		
		// TODO Auto-generated method stub
		
		while (PipeReader.tryReadFragment(input)) {
		    int msgIdx = PipeReader.getMsgIdx(input);
		    switch(msgIdx) {
		        case MQTTClientResponseSchema.MSG_MESSAGE_3:
		            MQTTClientResponseSchema.consumeMessage(input);
		        break;
		        case MQTTClientResponseSchema.MSG_ERROR_4:
		            MQTTClientResponseSchema.consumeError(input);
		        break;
		        case -1:
		           //requestShutdown();
		        break;
		    }
		    PipeReader.releaseReadLock(input);
		}
		
		
		
	}

}
