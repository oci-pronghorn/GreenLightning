package com.ociweb.gl.impl.stage;

import com.ociweb.gl.impl.schema.MessageSubscription;
import com.ociweb.pronghorn.network.schema.MQTTClientRequestSchema;
import com.ociweb.pronghorn.network.schema.MQTTClientResponseSchema;
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
	private final CharSequence internalTopic;
	private final CharSequence externalTopic;
	private final EgressConverter converter;
	
	private final int fieldQOS;
	private final int fieldRetain;
	
	private static final EgressConverter copyConverter = new EgressConverter() {

		@Override
		public void convert(DataInputBlobReader<MessageSubscription> inputStream,
							DataOutputBlobWriter<MQTTClientRequestSchema> outputStream) {
			
			inputStream.readInto(outputStream,inputStream.available());
			
		}
		
	};
	
	public EgressMQTTStage(GraphManager graphManager, Pipe<MessageSubscription> input, Pipe<MQTTClientRequestSchema> output,
							CharSequence internalTopic,	CharSequence externalTopic, int fieldQOS, int fieldRetain) {
		this(graphManager,input,output,internalTopic, externalTopic, copyConverter, fieldQOS, fieldRetain);
	}
	
	public EgressMQTTStage(GraphManager graphManager, Pipe<MessageSubscription> input, Pipe<MQTTClientRequestSchema> output,
							CharSequence internalTopic,	CharSequence externalTopic, EgressConverter converter,
							int fieldQOS, int fieldRetain) {
		super(graphManager, input, output);
		this.input = input;
		this.output = output;
		this.internalTopic = internalTopic;
		this.externalTopic = externalTopic;
		this.converter = converter;
		this.fieldQOS = fieldQOS;
		this.fieldRetain = fieldRetain;
		
	}

	@Override
	public void run() {
		
		while ( PipeWriter.hasRoomForWrite(output) &&
				PipeReader.tryReadFragment(input)) {
			
		    int msgIdx = PipeReader.getMsgIdx(input);
		    
		    switch(msgIdx) {
		        case MessageSubscription.MSG_PUBLISH_103:
		        	
		        	boolean topicMatches = PipeReader.isEqual(input, MessageSubscription.MSG_PUBLISH_103_FIELD_TOPIC_1, internalTopic);
		        	assert(topicMatches) : "unknown topic";
		            
		        	PipeWriter.presumeWriteFragment(output, MQTTClientRequestSchema.MSG_PUBLISH_3);
		        	PipeWriter.writeInt(output,MQTTClientRequestSchema.MSG_PUBLISH_3_FIELD_QOS_21, fieldQOS);
		        	PipeWriter.writeInt(output,MQTTClientRequestSchema.MSG_PUBLISH_3_FIELD_RETAIN_22, fieldRetain);
		        	PipeWriter.writeUTF8(output,MQTTClientRequestSchema.MSG_PUBLISH_3_FIELD_TOPIC_23, externalTopic);

		        	
		        //////////////////////
		        //converter
		        //////////////////////
		        	
				//debug
				//StringBuilder b = new StringBuilder("EgressMQTTStage ");
				//System.err.println(PipeReader.readUTF8(input, MessageSubscription.MSG_PUBLISH_103_FIELD_PAYLOAD_3 , b));
						
		        		
		        	
		        DataInputBlobReader<MessageSubscription> inputStream = PipeReader.inputStream(input, MessageSubscription.MSG_PUBLISH_103_FIELD_PAYLOAD_3);
		        
		        DataOutputBlobWriter<MQTTClientRequestSchema> outputStream = PipeWriter.outputStream(output);
		        DataOutputBlobWriter.openField(outputStream);
		        
		        converter.convert(inputStream,outputStream);
		        
		        DataOutputBlobWriter.closeHighLevelField(outputStream, MQTTClientRequestSchema.MSG_PUBLISH_3_FIELD_PAYLOAD_25);
		        
				
				///////////////////////
				///////////////////////
				///////////////////////
				
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
		
		
	}

}
