package com.ociweb.gl.api.blocking;

import com.ociweb.gl.impl.schema.MessagePrivate;
import com.ociweb.gl.impl.schema.MessageSubscription;
import com.ociweb.pronghorn.pipe.MessageSchema;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.stage.blocking.Blockable;
import com.ociweb.pronghorn.stage.blocking.BlockingSupportStage;
import com.ociweb.pronghorn.stage.blocking.Choosable;
import com.ociweb.pronghorn.stage.blocking.UnchosenMessage;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;

public class BlockableStageFactory {

   public static <Q extends MessageSchema<Q>, T extends MessageSchema<T>, P extends MessageSchema<P>> BlockingSupportStage<T, P, Q> buildBlockingSupportStage(
			GraphManager graphManager, long timeoutNS, int threadsCount, Pipe<T> input, Pipe<P> output, Pipe<Q> timeout,
			BlockingBehaviorProducer producer, Choosable<T> chooser) {
	   
		Blockable<T,P,Q>[] blockables = new Blockable[threadsCount];
		int c = threadsCount;
		while (--c >= 0) {
			blockables[c] = (Blockable<T, P, Q>)new BlockingBehaviorBridgePPP(producer.produce());
		}
 	    return new BlockingSupportStage<T,P,Q>(graphManager, input, output, timeout, timeoutNS, chooser, 
 	    		new UnchosenMessage<T>() {
					@Override
					public boolean message(Pipe<T> pipe) {
						if (Pipe.isForSchema(pipe, MessagePrivate.class)) {
							Pipe.markTail(pipe);
							int msgIdx = Pipe.takeMsgIdx(pipe);
							if (msgIdx>=0) {
								boolean result = producer.unChosenMessages(Pipe.openInputStream(pipe));
								if (result) {
									Pipe.confirmLowLevelRead(pipe, Pipe.sizeOf(pipe, msgIdx));
									Pipe.releaseReadLock(pipe);
								} else {
									Pipe.resetTail(pipe);
								}
								return result;
							} else {
								Pipe.skipNextFragment(pipe, msgIdx);
								return true;
							}							
						} else if (Pipe.isForSchema(pipe, MessageSubscription.class)) {
							Pipe.markTail(pipe);
							int msgIdx = Pipe.takeMsgIdx(pipe);
							if (msgIdx>=0) {
								Pipe.openInputStream(pipe);//skip over the topic we just want the payload.
								boolean result = producer.unChosenMessages(Pipe.openInputStream(pipe));
								if (result) {
									Pipe.confirmLowLevelRead(pipe, Pipe.sizeOf(pipe, msgIdx));
									Pipe.releaseReadLock(pipe);
								} else {
									Pipe.resetTail(pipe);
								}
								return result;
							} else {
								Pipe.skipNextFragment(pipe, msgIdx);
								return true;
							}
						} else {
							throw new UnsupportedOperationException("unknown schema "+Pipe.schemaName(pipe));
						}
					}
				
 	    		},   		
 	    		blockables);
	}

	public static <T extends MessageSchema<T>> int streamOffset(Pipe<T> input) {

		   //lookup the position of the stream 
		   if (Pipe.isForSchema(input, MessagePrivate.class)) {
			   return 0xFF & MessagePrivate.MSG_PUBLISH_1_FIELD_PAYLOAD_3;
		   } else if (Pipe.isForSchema(input, MessageSubscription.class)) {
			   return 0xFF & MessageSubscription.MSG_PUBLISH_103_FIELD_PAYLOAD_3;
		   } else {
			   throw new UnsupportedOperationException("Schema not supported: "+Pipe.schemaName(input));
		   }
		   ////////////////////
	}
	
}
