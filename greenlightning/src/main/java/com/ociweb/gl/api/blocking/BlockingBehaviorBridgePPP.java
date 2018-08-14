package com.ociweb.gl.api.blocking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.gl.impl.schema.MessagePrivate;
import com.ociweb.gl.impl.schema.MessagePubSub;
import com.ociweb.gl.impl.schema.MessageSubscription;
import com.ociweb.pronghorn.pipe.DataOutputBlobWriter;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.stage.blocking.Blockable;

public class BlockingBehaviorBridgePPP extends Blockable {
	
	private static final Logger logger = LoggerFactory.getLogger(BlockingBehaviorBridgePPP.class);
	
	private final BlockingBehavior bb;
	
	public BlockingBehaviorBridgePPP(BlockingBehavior bb) {
		this.bb = bb;
	}
	
	@Override
	public void begin(Pipe input) {	
		//logger.info("\n------------------begin");
		
		int id = Pipe.takeMsgIdx(input);	
		if (Pipe.isForSchema(input, MessagePrivate.instance)) {
			assert(MessagePrivate.MSG_PUBLISH_1 == id);
			bb.begin(Pipe.openInputStream(input));
			
			
			
		} else {
			assert(Pipe.isForSchema(input, MessageSubscription.instance));
			assert(MessageSubscription.MSG_PUBLISH_103 == id);
			//MessageSubscription.MSG_PUBLISH_103
		
		//	public static final int MSG_PUBLISH_103_FIELD_TOPIC_1 = 0x01400001;
		//	public static final int MSG_PUBLISH_103_FIELD_PAYLOAD_3 = 0x01c00003;
			
			
			//TODO: fix MQTT engress code...
			
			
		}
		Pipe.confirmLowLevelRead(input, Pipe.sizeOf(input, id));
		Pipe.releaseReadLock(input);
	}

	@Override
	public void run() throws InterruptedException {
		//logger.info("\n-----------------run");
		bb.run();
	}

	@Override
	public void finish(Pipe output) {
		//logger.info("\n-----------------finish");
		if (Pipe.isForSchema(output, MessagePrivate.instance)) {
			int size = Pipe.addMsgIdx(output, MessagePrivate.MSG_PUBLISH_1);
			DataOutputBlobWriter<MessagePrivate> stream = Pipe.openOutputStream(output);
			bb.finish(stream);
			DataOutputBlobWriter.closeLowLevelField(stream);
			Pipe.confirmLowLevelWrite(output,size);
			Pipe.publishWrites(output);
		} else {
			assert(Pipe.isForSchema(output, MessagePubSub.instance));
			
			//MessagePubSub.MSG_PUBLISH_103;
			
		
			
		}
	}

	@Override
	public void timeout(Pipe output) {
		//logger.info("\n-----------------timeout");
		if (Pipe.isForSchema(output, MessagePrivate.instance)) {
			int size = Pipe.addMsgIdx(output, MessagePrivate.MSG_PUBLISH_1);
			DataOutputBlobWriter<MessagePrivate> stream = Pipe.openOutputStream(output);
			bb.finish(stream);
			DataOutputBlobWriter.closeLowLevelField(stream);
		
			Pipe.confirmLowLevelWrite(output, size);
			Pipe.publishWrites(output);
		} else {
			assert(Pipe.isForSchema(output, MessagePubSub.instance));
		
			//MessagePubSub.MSG_PUBLISH_103;
		
	
		
		}
	}

}
