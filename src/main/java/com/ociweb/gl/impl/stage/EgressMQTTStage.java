package com.ociweb.gl.impl.stage;

import com.ociweb.gl.impl.schema.MessageSubscription;
import com.ociweb.pronghorn.network.schema.MQTTClientRequestSchema;
import com.ociweb.pronghorn.pipe.DataInputBlobReader;
import com.ociweb.pronghorn.pipe.DataOutputBlobWriter;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeReader;
import com.ociweb.pronghorn.pipe.PipeWriter;
import com.ociweb.pronghorn.stage.PronghornStage;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;

public class EgressMQTTStage extends PronghornStage {

	private final Pipe<MessageSubscription> input;
	private final Pipe<MQTTClientRequestSchema> output;
	private final CharSequence[] internalTopic;
	private final CharSequence[] externalTopic;
	private boolean allTopicsMatch;
	private final EgressConverter[] converter;
	
	private final int[] fieldQOS;
	private final int[] fieldRetain;
	
	public static final EgressConverter copyConverter = new EgressConverter() {

		@Override
		public void convert(DataInputBlobReader<MessageSubscription> inputStream,
							DataOutputBlobWriter<?> outputStream) {
			
			inputStream.readInto(outputStream,inputStream.available());
			
		}
		
	};
	
	public EgressMQTTStage(GraphManager graphManager, 
			               Pipe<MessageSubscription> input, Pipe<MQTTClientRequestSchema> output,
						   CharSequence[] internalTopic, 
						   CharSequence[] externalTopic, 
						   int[] fieldQOS, int[] fieldRetain) {
		this(graphManager,input,output,internalTopic, externalTopic, asArray(copyConverter,internalTopic.length), fieldQOS, fieldRetain);

	}
	
	private static EgressConverter[] asArray(EgressConverter copyconverter, int length) {
		EgressConverter[] array = new EgressConverter[length];
		while (--length>=0) {
			array[length] = copyconverter;
		}
		return array;
	}

	//TODO: must add private topics to this component at some point...
	
	public EgressMQTTStage(GraphManager graphManager, 
			               Pipe<MessageSubscription> input, 
			               Pipe<MQTTClientRequestSchema> output,
						   CharSequence[] internalTopic,	CharSequence[] externalTopic, EgressConverter[] converter,
							int[] fieldQOS, int[] fieldRetain) {
		super(graphManager, input, output);
		this.input = input;
		this.output = output;
		this.internalTopic = internalTopic;
		this.externalTopic = externalTopic;		
		this.converter = converter;
		this.fieldQOS = fieldQOS;
		this.fieldRetain = fieldRetain;

		this.allTopicsMatch = isMatching(internalTopic,externalTopic,converter,fieldQOS,fieldRetain);
		
		supportsBatchedRelease = false; //must have immediate release
		supportsBatchedPublish = false; //also we want to minimize outgoing latency.
				
	}

	private boolean isMatching(CharSequence[] internalTopic, CharSequence[] externalTopic,
			                   EgressConverter[] converter, int[] qos, int[] fieldRetain) {
		assert(internalTopic.length == externalTopic.length);
		int i = internalTopic.length;
		while (--i>=0) {
			CharSequence a = internalTopic[i];
			CharSequence b = externalTopic[i];
			if (a.length()!=b.length()) {
				return false;
			}
			int j = a.length();
			while (--j>=0) {
				if (a.charAt(j)!=b.charAt(j)) {
					return false;
				}	
			}
		}
		EgressConverter prototype = converter[0];
		int k = converter.length;
		while(--k>=0) {
			if (prototype!=converter[k]) {
				return false;
			}
		}
		
		int aQOS = qos[0];
		k = qos.length;
		while(--k>=0) {
			if (aQOS!=qos[k]) {
				return false;
			}
		}		
		
		int aRet = fieldRetain[0];
		k = fieldRetain.length;
		while(--k>=0) {
			if (aRet!=fieldRetain[k]) {
				return false;
			}
		}	
		
		return true;
	}

	@Override
	public void run() {
		
		boolean foundWork;

		do {
			foundWork = false;
			
			//TODO: this must not release the input until the MQTT broker has sent back the ack.
			
			while ( PipeWriter.hasRoomForWrite(output) &&
					PipeReader.tryReadFragment(input)) {
				
				foundWork = true;
			    int msgIdx = PipeReader.getMsgIdx(input);
			    
			    switch(msgIdx) {
			        case MessageSubscription.MSG_PUBLISH_103:
						int i = internalTopic.length;
						if (allTopicsMatch) {
							i = 0;//to select the common converter for all.
							PipeWriter.presumeWriteFragment(output, MQTTClientRequestSchema.MSG_PUBLISH_3);
							PipeWriter.writeInt(output, MQTTClientRequestSchema.MSG_PUBLISH_3_FIELD_QOS_21, fieldQOS[i]);
							PipeWriter.writeInt(output, MQTTClientRequestSchema.MSG_PUBLISH_3_FIELD_RETAIN_22, fieldRetain[i]);

							//direct copy of topic
							DataOutputBlobWriter<MQTTClientRequestSchema> stream = PipeWriter.outputStream(output);

							DataOutputBlobWriter.openField(stream);
							PipeReader.readUTF8(input, MessageSubscription.MSG_PUBLISH_103_FIELD_TOPIC_1, stream);
							DataOutputBlobWriter.closeHighLevelField(stream, MQTTClientRequestSchema.MSG_PUBLISH_3_FIELD_TOPIC_23);

						} else {
							boolean topicMatches = false;
							while (--i >= 0) { //TODO: this is very bad, swap out with trie parser instead of linear search
								if (PipeReader.isEqual(input, MessageSubscription.MSG_PUBLISH_103_FIELD_TOPIC_1, internalTopic[i])) {
									topicMatches = true;
									break;
								}
							}
							assert (topicMatches) : "ERROR, this topic was not known " + PipeReader.readUTF8(input, MessageSubscription.MSG_PUBLISH_103_FIELD_TOPIC_1, new StringBuilder());

							PipeWriter.presumeWriteFragment(output, MQTTClientRequestSchema.MSG_PUBLISH_3);
							PipeWriter.writeInt(output, MQTTClientRequestSchema.MSG_PUBLISH_3_FIELD_QOS_21, fieldQOS[i]);
							PipeWriter.writeInt(output, MQTTClientRequestSchema.MSG_PUBLISH_3_FIELD_RETAIN_22, fieldRetain[i]);
							PipeWriter.writeUTF8(output, MQTTClientRequestSchema.MSG_PUBLISH_3_FIELD_TOPIC_23, externalTopic[i]);
						}

						//////////////////////
						//converter
						//////////////////////

						//debug
						//StringBuilder b = new StringBuilder("EgressMQTTStage ");
						//System.err.println(PipeReader.readUTF8(input, MessageSubscription.MSG_PUBLISH_103_FIELD_PAYLOAD_3 , b));


						DataInputBlobReader<MessageSubscription> inputStream = PipeReader.inputStream(input, MessageSubscription.MSG_PUBLISH_103_FIELD_PAYLOAD_3);

						DataOutputBlobWriter<MQTTClientRequestSchema> outputStream = PipeWriter.outputStream(output);
						DataOutputBlobWriter.openField(outputStream);

						converter[i].convert(inputStream,outputStream);

						DataOutputBlobWriter.closeHighLevelField(outputStream, MQTTClientRequestSchema.MSG_PUBLISH_3_FIELD_PAYLOAD_25);

						PipeWriter.publishWrites(output);

					break;
			        case MessageSubscription.MSG_STATECHANGED_71:
			        	//int fieldOldOrdinal = PipeReader.readInt(input,MessageSubscription.MSG_STATECHANGED_71_FIELD_OLDORDINAL_8);
			        	//int fieldNewOrdinal = PipeReader.readInt(input,MessageSubscription.MSG_STATECHANGED_71_FIELD_NEWORDINAL_9);
			            //Ignore, state changes are not sent outside. 
			            
			        break;
			        case -1:
			           requestShutdown();
			        break;
			    }
			    PipeReader.releaseReadLock(input);
			}
	
			
		} while (foundWork);
	}
}
