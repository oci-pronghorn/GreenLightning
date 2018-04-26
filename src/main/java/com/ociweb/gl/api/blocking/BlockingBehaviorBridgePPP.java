package com.ociweb.gl.api.blocking;

import com.ociweb.gl.impl.schema.MessagePrivate;
import com.ociweb.gl.impl.schema.MessagePubSub;
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
	
	private final BlockingBehavior bb;
	
	public BlockingBehaviorBridgePPP(BlockingBehavior bb) {
		this.bb = bb;
	}
	
	@Override
	public void begin(Pipe<MessagePrivate> input) {		
		int id = Pipe.takeMsgIdx(input);	
		assert(MessagePrivate.MSG_PUBLISH_1 == id);
		bb.begin(Pipe.openInputStream(input));
		Pipe.confirmLowLevelRead(input, Pipe.sizeOf(input, MessagePrivate.MSG_PUBLISH_1));
		Pipe.releaseReadLock(input);
	}

	@Override
	public void run() throws InterruptedException {
		bb.run();
	}

	@Override
	public void finish(Pipe<MessagePrivate> output) {
		Pipe.addMsgIdx(output, MessagePrivate.MSG_PUBLISH_1);
		DataOutputBlobWriter<MessagePrivate> stream = Pipe.openOutputStream(output);
		bb.finish(stream);
		DataOutputBlobWriter.closeLowLevelField(stream);
		Pipe.confirmLowLevelWrite(output);
		Pipe.publishWrites(output);
	}

	@Override
	public void timeout(Pipe<MessagePrivate> output) {
		Pipe.addMsgIdx(output, MessagePrivate.MSG_PUBLISH_1);
		DataOutputBlobWriter<MessagePrivate> stream = Pipe.openOutputStream(output);
		bb.finish(stream);
		DataOutputBlobWriter.closeLowLevelField(stream);
		Pipe.confirmLowLevelWrite(output);
		Pipe.publishWrites(output);
	}

}
