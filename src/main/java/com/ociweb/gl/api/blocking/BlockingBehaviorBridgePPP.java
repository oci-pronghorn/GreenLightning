package com.ociweb.gl.api.blocking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.gl.impl.schema.MessagePrivate;
import com.ociweb.gl.impl.schema.MessageSubscription;
import com.ociweb.pronghorn.pipe.DataOutputBlobWriter;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.stage.blocking.Blockable;

//  newBlocking( blockClass, 


//8 patterns?
//Pipe<MessagePrivate> inout;
//Pipe<MessagePubSub> output;
//Pipe<MessageSubscription> input;


//adapter to map??
//need one per specific inputs...?
public class BlockingBehaviorBridgePPP extends Blockable<MessagePrivate, MessagePrivate, MessagePrivate> {
	
	private static final Logger logger = LoggerFactory.getLogger(BlockingBehaviorBridgePPP.class);
	
	private final BlockingBehavior bb;
	
	public BlockingBehaviorBridgePPP(BlockingBehavior bb) {
		this.bb = bb;
	}
	
	@Override
	public void begin(Pipe<MessagePrivate> input) {	
		//logger.info("\n------------------begin");
		
		if (Pipe.isForSchema(input, MessagePrivate.instance)) {
			int id = Pipe.takeMsgIdx(input);	
			assert(MessagePrivate.MSG_PUBLISH_1 == id);
			bb.begin(Pipe.openInputStream(input));
			
			Pipe.confirmLowLevelRead(input, Pipe.sizeOf(input, MessagePrivate.MSG_PUBLISH_1));
			Pipe.releaseReadLock(input);
			
			
		} else {
		
			//MessageSubscription.MSG_PUBLISH_103
		
			
		}
	}

	@Override
	public void run() throws InterruptedException {
		//logger.info("\n-----------------run");
		bb.run();
	}

	@Override
	public void finish(Pipe<MessagePrivate> output) {
		//logger.info("\n-----------------finish");
		int size = Pipe.addMsgIdx(output, MessagePrivate.MSG_PUBLISH_1);
		DataOutputBlobWriter<MessagePrivate> stream = Pipe.openOutputStream(output);
		bb.finish(stream);
		DataOutputBlobWriter.closeLowLevelField(stream);
		Pipe.confirmLowLevelWrite(output,size);
		Pipe.publishWrites(output);
	}

	@Override
	public void timeout(Pipe<MessagePrivate> output) {
		//logger.info("\n-----------------timeout");
		int size = Pipe.addMsgIdx(output, MessagePrivate.MSG_PUBLISH_1);
		DataOutputBlobWriter<MessagePrivate> stream = Pipe.openOutputStream(output);
		bb.finish(stream);
		DataOutputBlobWriter.closeLowLevelField(stream);
		
		
		Pipe.confirmLowLevelWrite(output, size);
		Pipe.publishWrites(output);
		
	}

}
