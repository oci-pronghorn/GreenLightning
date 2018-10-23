package com.ociweb.gl.impl.blocking;

import com.ociweb.gl.api.MsgRuntime;
import com.ociweb.gl.api.PubSubFixedTopicService;
import com.ociweb.gl.api.ShutdownListener;
import com.ociweb.gl.api.TickListener;
import com.ociweb.gl.impl.BuilderImpl;
import com.ociweb.pronghorn.pipe.DataInputBlobReader;
import com.ociweb.pronghorn.pipe.DataOutputBlobWriter;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeConfigManager;
import com.ociweb.pronghorn.pipe.RawDataSchema;
import com.ociweb.pronghorn.stage.PronghornStage;

public class JoinBlockingBehavior implements TickListener {

	private Pipe<RawDataSchema>[] inputPipes;
	private PubSubFixedTopicService[] targetService;
	private TargetSelector selector;
	
	
	public JoinBlockingBehavior(MsgRuntime<?,?,?> msgRuntime,
			                    Pipe<RawDataSchema>[] inputPipes, 
			                    String[] targetTopics,
			                    TargetSelector selector) {
		
		this.inputPipes = inputPipes;
		this.selector = selector;
		
      	PipeConfigManager pcm = new PipeConfigManager(4, MsgRuntime.defaultCommandChannelLength, MsgRuntime.defaultCommandChannelMaxPayload);
      	BuilderImpl builder = MsgRuntime.builder(msgRuntime);
      	
      	int dataLen = PronghornStage.maxVarLength(inputPipes);      	
      	int count = inputPipes[0].config().minimumFragmentsOnPipe();
      	
      	//build each of the target publish services so they can be indexed by targetTopics position
      	targetService = new PubSubFixedTopicService[targetTopics.length];
      	int i = targetTopics.length;
      	while (--i>=0) {
      		targetService[i] = builder.newCommandChannel(msgRuntime.constructingParallelInstance(),  pcm)
      				                  .newPubSubService(targetTopics[i], count, dataLen);
      	
      	}
      	
	}
	
	@Override
	public void tickEvent() {

		//pick up work and publish it
				
		int x = inputPipes.length;
		while (--x>=0) {
			Pipe<RawDataSchema> p = inputPipes[x];
			while (Pipe.hasContentToRead(inputPipes[x])) {
				
				DataInputBlobReader<RawDataSchema> peekInputStream = Pipe.peekInputStream(p, 0xFF&RawDataSchema.MSG_CHUNKEDSTREAM_1_FIELD_BYTEARRAY_2);
				
				if (Pipe.peekMsg(p, RawDataSchema.MSG_CHUNKEDSTREAM_1)) {
					
					int absPos = peekInputStream.absolutePosition();
					int idx = selector.pickTargetIdx(peekInputStream);
					peekInputStream.absolutePosition(absPos);
					
					boolean ok = targetService[idx].publishTopic(w -> {
						peekInputStream.readInto(w,peekInputStream.available());
						peekInputStream.readFromEndInto((DataOutputBlobWriter)w); //NOTE: this moves struct across.	
					
					} ); //TODO: needs to copy struct
					if (ok) {
						//remove since we published it 
						Pipe.skipNextFragment(p);
					} else {
						break;
					}
				} else if (Pipe.peekMsg(p, -1)) {
					Pipe.skipNextFragment(p);					
				} else {
					//unknown
				}
			}	
		}
	}


}
