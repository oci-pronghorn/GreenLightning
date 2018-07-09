package com.ociweb.gl.impl.mqtt;

import com.ociweb.gl.api.PubSubListener;
import com.ociweb.gl.impl.stage.EgressConverter;
import com.ociweb.pronghorn.network.schema.MQTTClientRequestSchema;
import com.ociweb.pronghorn.pipe.ChannelReader;
import com.ociweb.pronghorn.pipe.DataOutputBlobWriter;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeWriter;
import com.ociweb.pronghorn.util.TrieParser;
import com.ociweb.pronghorn.util.TrieParserReader;
import com.ociweb.pronghorn.util.TrieParserReaderLocal;

public class EgressMQTTBehavior implements PubSubListener {

	
	private final CharSequence[] internalTopic;
	private final CharSequence[] externalTopic;
	private boolean allTopicsMatch;
	private final EgressConverter[] converter;
	
	private final int[] fieldQOS;
	private final int[] fieldRetain;
	
	private final Pipe<MQTTClientRequestSchema> output;
	private final TrieParser map;
	
	public static final EgressConverter copyConverter = new EgressConverter() {

		@Override
		public void convert(ChannelReader inputStream,
							DataOutputBlobWriter<?> outputStream) {
			
			inputStream.readInto(outputStream,inputStream.available());
			
		}
		
	};
	
	public EgressMQTTBehavior(CharSequence[] internalTopic, 
						    CharSequence[] externalTopic, 
						   int[] fieldQOS, int[] fieldRetain,
						   EgressConverter[] converter,
						   Pipe<MQTTClientRequestSchema> output) {
		
		this.internalTopic = internalTopic;
		this.externalTopic = externalTopic;
		this.fieldQOS = fieldQOS;
		this.fieldRetain = fieldRetain;
		this.allTopicsMatch = isMatching(internalTopic,externalTopic,converter,fieldQOS,fieldRetain);
		this.converter = converter;
		this.output = output;
		
		if (!this.allTopicsMatch) {			
			boolean ignoreCase = false; //MQTT is case sensitive
			boolean skipDeepChecks = true; //this behavior is only subscribed to these topics so no others will appear
			map = new TrieParser(externalTopic.length*20,2,skipDeepChecks,ignoreCase,ignoreCase);
			int i = internalTopic.length;
			while (--i>=0) {
				map.setUTF8Value(internalTopic[i], i);
			}
		} else {
			map = null;
		}
	}
	
	private boolean isMatching(CharSequence[] internalTopic, CharSequence[] externalTopic, EgressConverter[] converter,
			int[] qos, int[] fieldRetain) {
		assert (internalTopic.length == externalTopic.length);
		int i = internalTopic.length;
		while (--i >= 0) {
			CharSequence a = internalTopic[i];
			CharSequence b = externalTopic[i];
			if (a.length() != b.length()) {
				return false;
			}
			int j = a.length();
			while (--j >= 0) {
				if (a.charAt(j) != b.charAt(j)) {
					return false;
				}
			}
		}
		EgressConverter prototype = converter[0];
		int k = converter.length;
		while (--k >= 0) {
			if (prototype != converter[k]) {
				return false;
			}
		}

		int aQOS = qos[0];
		k = qos.length;
		while (--k >= 0) {
			if (aQOS != qos[k]) {
				return false;
			}
		}

		int aRet = fieldRetain[0];
		k = fieldRetain.length;
		while (--k >= 0) {
			if (aRet != fieldRetain[k]) {
				return false;
			}
		}

		return true;
	}
	
	@Override
	public boolean message(CharSequence topic, ChannelReader payload) {
	
		if (PipeWriter.hasRoomForWrite(output)) {
			
			int i = 0;
			if (allTopicsMatch) {
				i = 0;//to select the common converter for all.
				
				PipeWriter.presumeWriteFragment(output, MQTTClientRequestSchema.MSG_PUBLISH_3);
				
				PipeWriter.writeInt(output, MQTTClientRequestSchema.MSG_PUBLISH_3_FIELD_QOS_21, fieldQOS[i]);
				PipeWriter.writeInt(output, MQTTClientRequestSchema.MSG_PUBLISH_3_FIELD_RETAIN_22, fieldRetain[i]);
				
				//direct copy of topic
				DataOutputBlobWriter<MQTTClientRequestSchema> stream = PipeWriter.outputStream(output);
				DataOutputBlobWriter.openField(stream);
				stream.writeUTF(topic);
				DataOutputBlobWriter.closeHighLevelField(stream, MQTTClientRequestSchema.MSG_PUBLISH_3_FIELD_TOPIC_23);
				
			} else {
				//TODO: if the topic is private it is possible to have an even tighter connection without use of this TrieParser
				i = (int)TrieParserReaderLocal.get().query(map, topic);				
				assert (i>=0) : "ERROR, this topic was not known " + topic;
				
				PipeWriter.presumeWriteFragment(output, MQTTClientRequestSchema.MSG_PUBLISH_3);
				
				PipeWriter.writeInt(output, MQTTClientRequestSchema.MSG_PUBLISH_3_FIELD_QOS_21, fieldQOS[i]);
				PipeWriter.writeInt(output, MQTTClientRequestSchema.MSG_PUBLISH_3_FIELD_RETAIN_22, fieldRetain[i]);
				PipeWriter.writeUTF8(output, MQTTClientRequestSchema.MSG_PUBLISH_3_FIELD_TOPIC_23, externalTopic[i]);
			}
			
			//////////////////////
			//converter
			//////////////////////
			
			
			DataOutputBlobWriter<MQTTClientRequestSchema> outputStream = PipeWriter.outputStream(output);
			DataOutputBlobWriter.openField(outputStream);
			
			converter[i].convert(payload, outputStream);
			
			DataOutputBlobWriter.closeHighLevelField(outputStream, MQTTClientRequestSchema.MSG_PUBLISH_3_FIELD_PAYLOAD_25);
			
			PipeWriter.publishWrites(output);
			
			return true;
		} else {
			return false;
		}
	}

}
