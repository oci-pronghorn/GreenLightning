package com.ociweb.gl.impl.stage;

import com.ociweb.gl.impl.schema.MessageSubscription;
import com.ociweb.pronghorn.network.schema.MQTTClientRequestSchema;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeReader;
import com.ociweb.pronghorn.stage.PronghornStage;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;

public class EgressMQTTStage extends PronghornStage {

	Pipe<MessageSubscription> input;
	Pipe<MQTTClientRequestSchema> output;
	
	public EgressMQTTStage(GraphManager graphManager, Pipe<MessageSubscription> input, Pipe<MQTTClientRequestSchema> output) {
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
		        case MessageSubscription.MSG_PUBLISH_103:
		            MessageSubscription.consumePublish(input);
		        break;
		        case MessageSubscription.MSG_STATECHANGED_71:
		            MessageSubscription.consumeStateChanged(input);
		        break;
		        case -1:
		           //requestShutdown();
		        break;
		    }
		    PipeReader.releaseReadLock(input);
		}
		
		
	}

}
