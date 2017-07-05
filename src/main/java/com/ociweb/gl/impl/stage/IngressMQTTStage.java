package com.ociweb.gl.impl.stage;

import com.ociweb.gl.impl.schema.IngressMessages;
import com.ociweb.pronghorn.network.schema.MQTTClientResponseSchema;
import com.ociweb.pronghorn.pipe.DataInputBlobReader;
import com.ociweb.pronghorn.pipe.DataOutputBlobWriter;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeReader;
import com.ociweb.pronghorn.pipe.PipeWriter;
import com.ociweb.pronghorn.stage.PronghornStage;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;

public class IngressMQTTStage extends PronghornStage {

	private final Pipe<MQTTClientResponseSchema> input;
	private final Pipe<IngressMessages> output;
	private final CharSequence externalTopic; 
	private final CharSequence internalTopic;
	private final IngressConverter converter;
	
	private static final IngressConverter directCopy = new IngressConverter() {		
		@Override
		public void convertData(DataInputBlobReader<MQTTClientResponseSchema> inputStream,
								DataOutputBlobWriter<IngressMessages> outputStream) {
			
			inputStream.readInto(outputStream, inputStream.available());
			
			
		}
	};
		
	public IngressMQTTStage(GraphManager graphManager, Pipe<MQTTClientResponseSchema> input, Pipe<IngressMessages> output, 
            CharSequence externalTopic, CharSequence internalTopic) {
		this(graphManager, input, output, externalTopic, internalTopic, directCopy);
	}
	
	public IngressMQTTStage(GraphManager graphManager, Pipe<MQTTClientResponseSchema> input, Pipe<IngressMessages> output, 
			                CharSequence externalTopic, CharSequence internalTopic, IngressConverter converter) {
		
		super(graphManager, input, output);
		this.input = input;
		this.output = output;
		this.externalTopic = externalTopic;
		this.internalTopic = internalTopic;
		this.converter = converter;
		
	}

	@Override
	public void run() {
	
		while (PipeWriter.hasRoomForWrite(output) &&
			   PipeReader.tryReadFragment(input)) {
		    int msgIdx = PipeReader.getMsgIdx(input);
		    switch(msgIdx) {
		        case MQTTClientResponseSchema.MSG_MESSAGE_3:
					//int fieldQOS = PipeReader.readInt(input,MQTTClientResponseSchema.MSG_MESSAGE_3_FIELD_QOS_21);
					//int fieldRetain = PipeReader.readInt(input,MQTTClientResponseSchema.MSG_MESSAGE_3_FIELD_RETAIN_22);
					//int fieldDup = PipeReader.readInt(input,MQTTClientResponseSchema.MSG_MESSAGE_3_FIELD_DUP_36);
					
					boolean isTopic = PipeReader.isEqual(input, MQTTClientResponseSchema.MSG_MESSAGE_3_FIELD_TOPIC_23, externalTopic);
					assert(isTopic) : "Should only receive messages on this topic";

					
					PipeWriter.presumeWriteFragment(output, IngressMessages.MSG_PUBLISH_103);
					PipeWriter.writeUTF8(output,IngressMessages.MSG_PUBLISH_103_FIELD_TOPIC_1, internalTopic);
					
					//////////////////
					//copy and convert the data
					//////////////////

					//debug
					//StringBuilder b = new StringBuilder("IngressMQTTStage ");
					//System.err.println(PipeReader.readUTF8(input, MQTTClientResponseSchema.MSG_MESSAGE_3_FIELD_PAYLOAD_25 , b));
										
					DataInputBlobReader<MQTTClientResponseSchema> inputStream = PipeReader.inputStream(input, MQTTClientResponseSchema.MSG_MESSAGE_3_FIELD_PAYLOAD_25);
					DataOutputBlobWriter<IngressMessages> outputStream = PipeWriter.outputStream(output);
					DataOutputBlobWriter.openField(outputStream);
					
					converter.convertData(inputStream, outputStream);
															
					int length = DataOutputBlobWriter.closeHighLevelField(outputStream, IngressMessages.MSG_PUBLISH_103_FIELD_PAYLOAD_3);

					/////////////////////
					/////////////////////
					/////////////////////
					
					PipeWriter.publishWrites(output);					
		            
		        break;
		        case MQTTClientResponseSchema.MSG_ERROR_4:
					int fieldErrorCode = PipeReader.readInt(input,MQTTClientResponseSchema.MSG_ERROR_4_FIELD_ERRORCODE_41);
					StringBuilder fieldErrorText = PipeReader.readUTF8(input,MQTTClientResponseSchema.MSG_ERROR_4_FIELD_ERRORTEXT_42,new StringBuilder(PipeReader.readBytesLength(input,MQTTClientResponseSchema.MSG_ERROR_4_FIELD_ERRORTEXT_42)));
			            
					//TODO: what should we do with these errors?
					
					
		        break;
		        case -1:
		           requestShutdown();
		        break;
		    }
		    PipeReader.releaseReadLock(input);
		}
		
		
		
	}


}
