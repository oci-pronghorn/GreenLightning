package com.ociweb.gl.api.blocking;

import com.ociweb.gl.impl.schema.MessagePrivate;
import com.ociweb.gl.impl.schema.MessageSubscription;
import com.ociweb.pronghorn.pipe.MessageSchema;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.stage.blocking.Blockable;
import com.ociweb.pronghorn.stage.blocking.BlockingSupportStage;
import com.ociweb.pronghorn.stage.blocking.Choosable;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;

public class BlockableStageFactory {

   public static <Q extends MessageSchema<Q>, T extends MessageSchema<T>, P extends MessageSchema<P>> BlockingSupportStage<T, P, Q> buildBlockingSupportStage(
			GraphManager graphManager, long timeoutNS, int threadsCount, Pipe<T> input, Pipe<P> output, Pipe<Q> timeout,
			BlockingBehaviorProducer producer, Choosable<T> chooser) {
		Blockable<T,P,Q>[] blockables = new Blockable[threadsCount];
		   //8 choices and put them into an array, only supporting 1 today, do rest later
		   if (Pipe.isForSchema(input, MessagePrivate.class)) {
			   if (Pipe.isForSchema(output, MessagePrivate.class)) {
				   if (Pipe.isForSchema(timeout, MessagePrivate.class)) {
					   int c = threadsCount;
					   while (--c >= 0) {
						   blockables[c] = (Blockable<T, P, Q>)new BlockingBehaviorBridgePPP(producer.produce());
					   }
				   } else {
					   //TODO: need to implment for pubsub
					   throw new UnsupportedOperationException("Not yet implemented");
				   }
			   } else {
				 //TODO: need to implment for pubsub
				   throw new UnsupportedOperationException("Not yet implemented");
			   }
		   } else {
			 //TODO: need to implment for pubsub
			   throw new UnsupportedOperationException("Not yet implemented");
		   }
		   return new BlockingSupportStage<T,P,Q>(graphManager, input, output, timeout, timeoutNS, chooser, blockables);
	}

	public static <T extends MessageSchema<T>> int streamOffset(Pipe<T> input) {
		////////////////////
		   //lookup the position of the stream 
		   int offsetToStream = -1;
		   if (Pipe.isForSchema(input, MessagePrivate.class)) {
			   offsetToStream = 0xFF & MessagePrivate.MSG_PUBLISH_1_FIELD_PAYLOAD_3;
		   } else if (Pipe.isForSchema(input, MessageSubscription.class)) {
			   offsetToStream = 0xFF & MessageSubscription.MSG_PUBLISH_103_FIELD_PAYLOAD_3;
		   } else {
			   throw new UnsupportedOperationException("Schema not supported: "+Pipe.schemaName(input));
		   }
		   ////////////////////
		return offsetToStream;
	}
	
}
