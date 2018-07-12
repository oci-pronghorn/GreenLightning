package com.ociweb.gl.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.gl.impl.PubSubMethodListenerBase;
import com.ociweb.gl.impl.schema.IngressMessages;
import com.ociweb.gl.impl.schema.MessagePrivate;
import com.ociweb.gl.impl.schema.MessagePubSub;
import com.ociweb.gl.impl.schema.MessageSubscription;
import com.ociweb.pronghorn.pipe.DataOutputBlobWriter;
import com.ociweb.pronghorn.pipe.FieldReferenceOffsetManager;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeWriter;

public class PubSubFixedTopicService {

	private static final Logger logger = LoggerFactory.getLogger(PubSubService.class);
	private final MsgCommandChannel<?> msgCommandChannel;
	private final String topic;
    private final byte[] topicBytes;
    private int topicToken = -2; //lazy loaded by design
	
	
	public PubSubFixedTopicService(MsgCommandChannel<?> msgCommandChannel, String topic) {
	
		this.msgCommandChannel = msgCommandChannel;
		msgCommandChannel.initFeatures |= MsgCommandChannel.DYNAMIC_MESSAGING;
		this.topic = topic;
		this.topicBytes = topic.getBytes();
		
		msgCommandChannel.builder.possiblePrivateTopicProducer(msgCommandChannel, topic);

	}
	
	public PubSubFixedTopicService(MsgCommandChannel<?> msgCommandChannel, String topic,
								   int queueLength, int maxMessageSize) {
		
		this.msgCommandChannel = msgCommandChannel;
		this.topic = topic;
		this.topicBytes = topic.getBytes();
		
		MsgCommandChannel.growCommandCountRoom(msgCommandChannel, queueLength);
		msgCommandChannel.initFeatures |= MsgCommandChannel.DYNAMIC_MESSAGING;  
		
		msgCommandChannel.pcm.ensureSize(MessagePubSub.class, queueLength, maxMessageSize);
		
		//also ensure consumers have pipes which can consume this.    		
		msgCommandChannel.pcm.ensureSize(MessageSubscription.class, queueLength, maxMessageSize);
		
		//IngressMessages Confirm that MQTT ingress is big enough as well			
		msgCommandChannel.pcm.ensureSize(IngressMessages.class, queueLength, maxMessageSize);
		
		msgCommandChannel.builder.possiblePrivateTopicProducer(msgCommandChannel, topic);

		
		
	}


	private final int token() {
		if (-2 == topicToken) {
			topicToken = computeToken();
		}
		return topicToken;
	}

	private int computeToken() {
		return null == msgCommandChannel.publishPrivateTopics ? -1 : msgCommandChannel.publishPrivateTopics.getToken(topicBytes, 0, topicBytes.length);
	}

	
	/**
	 * A method to determine if there is enough room in the pipe for more data
	 * @param messageCount int arg used in FieldReferenceOffsetManager.maxFragmentSize
	 * @return null==msgCommandChannel.goPipe || Pipe.hasRoomForWrite(msgCommandChannel.goPipe, FieldReferenceOffsetManager.maxFragmentSize(Pipe.from(msgCommandChannel.goPipe))*messageCount)
	 */
	public boolean hasRoomFor(int messageCount) {
		
		int token = token();
		if (token>=0) {
			//private topics use their own pipe which must be checked.
			return Pipe.hasRoomForWrite(msgCommandChannel.publishPrivateTopics.getPipe(token), messageCount * Pipe.sizeOf(MessagePrivate.instance, MessagePrivate.MSG_PUBLISH_1));
		}
		
		return null==msgCommandChannel.goPipe || Pipe.hasRoomForWrite(msgCommandChannel.goPipe, 
		FieldReferenceOffsetManager.maxFragmentSize(Pipe.from(msgCommandChannel.goPipe))*messageCount);
	}

	/**
	 * A method used to subscribe to a specified topic
	 * @return true if msgCommandChannel.goPipe == null || PipeWriter.hasRoomForWrite(msgCommandChannel.goPipe) <p> else false
	 */
	public boolean subscribe() {
		assert((0 != (msgCommandChannel.initFeatures & MsgCommandChannel.DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
		
		if (null==msgCommandChannel.listener) {
			throw new UnsupportedOperationException("Can not subscribe before startup. Call addSubscription when registering listener."); 
		}
		
		assert((0 != (msgCommandChannel.initFeatures & MsgCommandChannel.DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
		
		if (msgCommandChannel.goHasRoom() 
			&& PipeWriter.tryWriteFragment(msgCommandChannel.messagePubSub, MessagePubSub.MSG_SUBSCRIBE_100)) {
		    
		    PipeWriter.writeInt(msgCommandChannel.messagePubSub, MessagePubSub.MSG_SUBSCRIBE_100_FIELD_SUBSCRIBERIDENTITYHASH_4, System.identityHashCode((PubSubMethodListenerBase)msgCommandChannel.listener));
		    //OLD -- PipeWriter.writeUTF8(messagePubSub, MessagePubSub.MSG_SUBSCRIBE_100_FIELD_TOPIC_1, topic);
		    
		    DataOutputBlobWriter<MessagePubSub> output = PipeWriter.outputStream(msgCommandChannel.messagePubSub);
			output.openField();
			output.write(topicBytes, 0, topicBytes.length);
			
			MsgCommandChannel.publicTrackedTopicSuffix(msgCommandChannel, output);
			
			output.closeHighLevelField(MessagePubSub.MSG_SUBSCRIBE_100_FIELD_TOPIC_1);
		    
		    PipeWriter.publishWrites(msgCommandChannel.messagePubSub);
		    
		    msgCommandChannel.builder.releasePubSubTraffic(1, msgCommandChannel);
		    
		    return true;
		}        
		return false;	
	}

	/**
	 * A method used to subscribe to a specified topic with a listener
	 * @param listener PubSubMethodListenerBase arg used in PipeWriter.writeInt
	 * @return true if msgCommandChannel.goPipe == null || PipeWriter.hasRoomForWrite(msgCommandChannel.goPipe) <p> else false
	 */
	public boolean subscribe(PubSubMethodListenerBase listener) {
		assert((0 != (msgCommandChannel.initFeatures & MsgCommandChannel.DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
		
		if (msgCommandChannel.goHasRoom()  
			&& PipeWriter.tryWriteFragment(msgCommandChannel.messagePubSub, MessagePubSub.MSG_SUBSCRIBE_100)) {
		    
		    PipeWriter.writeInt(msgCommandChannel.messagePubSub, MessagePubSub.MSG_SUBSCRIBE_100_FIELD_SUBSCRIBERIDENTITYHASH_4, System.identityHashCode(listener));
		    //OLD -- PipeWriter.writeUTF8(messagePubSub, MessagePubSub.MSG_SUBSCRIBE_100_FIELD_TOPIC_1, topic);
		    
		    DataOutputBlobWriter<MessagePubSub> output = PipeWriter.outputStream(msgCommandChannel.messagePubSub);
			output.openField();	    		
			output.write(topicBytes, 0, topicBytes.length);
			
			MsgCommandChannel.publicTrackedTopicSuffix(msgCommandChannel, output);
			
			output.closeHighLevelField(MessagePubSub.MSG_SUBSCRIBE_100_FIELD_TOPIC_1);
		    
		    PipeWriter.publishWrites(msgCommandChannel.messagePubSub);
		    
		    msgCommandChannel.builder.releasePubSubTraffic(1, msgCommandChannel);
		    
		    return true;
		}        
		return false;	
	}

	/**
	 * A method used to unsubscribe from a specific topic
	 * @return true if msgCommandChannel.goPipe == null || PipeWriter.hasRoomForWrite(msgCommandChannel.goPipe) <p> else false
	 */
	public boolean unsubscribe() {
		assert((0 != (msgCommandChannel.initFeatures & MsgCommandChannel.DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
		
		assert((0 != (msgCommandChannel.initFeatures & MsgCommandChannel.DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
		
		if (msgCommandChannel.goHasRoom()  
			&& PipeWriter.tryWriteFragment(msgCommandChannel.messagePubSub, MessagePubSub.MSG_UNSUBSCRIBE_101)) {
		    
		    PipeWriter.writeInt(msgCommandChannel.messagePubSub, MessagePubSub.MSG_SUBSCRIBE_100_FIELD_SUBSCRIBERIDENTITYHASH_4, System.identityHashCode((PubSubMethodListenerBase)msgCommandChannel.listener));
		   //OLD  PipeWriter.writeUTF8(messagePubSub, MessagePubSub.MSG_UNSUBSCRIBE_101_FIELD_TOPIC_1, topic);
		    DataOutputBlobWriter<MessagePubSub> output = PipeWriter.outputStream(msgCommandChannel.messagePubSub);
			output.openField();	    		
			output.write(topicBytes, 0, topicBytes.length);
			
			MsgCommandChannel.publicTrackedTopicSuffix(msgCommandChannel, output);
			
			output.closeHighLevelField(MessagePubSub.MSG_UNSUBSCRIBE_101_FIELD_TOPIC_1);
		    
		    PipeWriter.publishWrites(msgCommandChannel.messagePubSub);
		    
		    msgCommandChannel.builder.releasePubSubTraffic(1, msgCommandChannel);
		    
		    return true;
		}        
		return false;	
	}

	/**
	 * A method used to unsubscribe from a specific topic and listener
	 * @param listener PubSubMethodListenerBase arg used in PipeWriter.writeInt
	 * @return true if msgCommandChannel.goPipe == null || PipeWriter.hasRoomForWrite(msgCommandChannel.goPipe) <p> else false
	 */
	public boolean unsubscribe(PubSubMethodListenerBase listener) {
		assert((0 != (msgCommandChannel.initFeatures & MsgCommandChannel.DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
		
		if (msgCommandChannel.goHasRoom()  
			&& PipeWriter.tryWriteFragment(msgCommandChannel.messagePubSub, MessagePubSub.MSG_UNSUBSCRIBE_101)) {
		    
		    PipeWriter.writeInt(msgCommandChannel.messagePubSub, MessagePubSub.MSG_SUBSCRIBE_100_FIELD_SUBSCRIBERIDENTITYHASH_4, System.identityHashCode(listener));
		   //OLD  PipeWriter.writeUTF8(messagePubSub, MessagePubSub.MSG_UNSUBSCRIBE_101_FIELD_TOPIC_1, topic);
		    DataOutputBlobWriter<MessagePubSub> output = PipeWriter.outputStream(msgCommandChannel.messagePubSub);
			output.openField();	    		
			output.write(topicBytes, 0, topicBytes.length);
			
			MsgCommandChannel.publicTrackedTopicSuffix(msgCommandChannel, output);
			
			output.closeHighLevelField(MessagePubSub.MSG_UNSUBSCRIBE_101_FIELD_TOPIC_1);
		    
		    PipeWriter.publishWrites(msgCommandChannel.messagePubSub);
		    
		    msgCommandChannel.builder.releasePubSubTraffic(1, msgCommandChannel);
		    
		    return true;
		}        
		return false;	
	}

    /**
     * Publishes specified failable topic with data written onto this channel
     * @param writable to write data into this channel
     * @return result if msgCommandChannel.goHasRoom else FailableWrite.Retry
     */
	public FailableWrite publishFailableTopic(FailableWritable writable) {
		assert((0 != (msgCommandChannel.initFeatures & MsgCommandChannel.DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
		assert(writable != null);
		
		int token =  token();
		
		if (token>=0) {
			return msgCommandChannel.publishFailableOnPrivateTopic(token, writable);
		} else {
			if (msgCommandChannel.goHasRoom() && PipeWriter.hasRoomForWrite(msgCommandChannel.messagePubSub)) {
				PubSubWriter pw = (PubSubWriter) Pipe.outputStream(msgCommandChannel.messagePubSub);
		
				DataOutputBlobWriter.openField(pw);
				FailableWrite result = writable.write(pw);
		
				if (result == FailableWrite.Cancel) {
					msgCommandChannel.messagePubSub.closeBlobFieldWrite();
				}
				else {
					PipeWriter.presumeWriteFragment(msgCommandChannel.messagePubSub, MessagePubSub.MSG_PUBLISH_103);
					PipeWriter.writeInt(msgCommandChannel.messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_QOS_5, WaitFor.All.policy());
					
		    		DataOutputBlobWriter<MessagePubSub> output = PipeWriter.outputStream(msgCommandChannel.messagePubSub);
		    		output.openField();	    		
		    		output.write(topicBytes, 0, topicBytes.length);
		    		
		    		MsgCommandChannel.publicTrackedTopicSuffix(msgCommandChannel, output);
		    		
		    		output.closeHighLevelField(MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1);
										
					//OLD PipeWriter.writeUTF8(messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1, topic);
		
					DataOutputBlobWriter.closeHighLevelField(pw, MessagePubSub.MSG_PUBLISH_103_FIELD_PAYLOAD_3);
		
					PipeWriter.publishWrites(msgCommandChannel.messagePubSub);
		
					MsgCommandChannel.publishGo(1, msgCommandChannel.builder.pubSubIndex(), msgCommandChannel);
				}
				return result;
			} else {
				return FailableWrite.Retry;
			}
		}
	}

	/**
     * Publishes specified failable topic with data written onto this channel and waits for success or failure
     * @param writable to write data into this channel
	 * @param ap WaitFor arg used in PipeWriter.writeInt
	 * @return result if msgCommandChannel.goHasRoom else FailableWrite.Retry
	 */
	public FailableWrite publishFailableTopic(FailableWritable writable, WaitFor ap) {
		assert((0 != (msgCommandChannel.initFeatures & MsgCommandChannel.DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
		assert(writable != null);
		
		int token =  token();
		
		if (token>=0) {
			return msgCommandChannel.publishFailableOnPrivateTopic(token, writable);
		} else {
			if (msgCommandChannel.goHasRoom() 
				&& PipeWriter.hasRoomForWrite(msgCommandChannel.messagePubSub)) {
				PubSubWriter pw = (PubSubWriter) Pipe.outputStream(msgCommandChannel.messagePubSub);
		
				DataOutputBlobWriter.openField(pw);
				FailableWrite result = writable.write(pw);
		
				if (result == FailableWrite.Cancel) {
					msgCommandChannel.messagePubSub.closeBlobFieldWrite();
				}
				else {
					PipeWriter.presumeWriteFragment(msgCommandChannel.messagePubSub, MessagePubSub.MSG_PUBLISH_103);
					PipeWriter.writeInt(msgCommandChannel.messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_QOS_5, ap.policy());
					
		    		DataOutputBlobWriter<MessagePubSub> output = PipeWriter.outputStream(msgCommandChannel.messagePubSub);
		    		output.openField();	    		
		    		output.write(topicBytes, 0, topicBytes.length);
		    		
		    		MsgCommandChannel.publicTrackedTopicSuffix(msgCommandChannel, output);
		    		
		    		output.closeHighLevelField(MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1);
										
					//OLD PipeWriter.writeUTF8(messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1, topic);
		
					DataOutputBlobWriter.closeHighLevelField(pw, MessagePubSub.MSG_PUBLISH_103_FIELD_PAYLOAD_3);
		
					PipeWriter.publishWrites(msgCommandChannel.messagePubSub);
		
					MsgCommandChannel.publishGo(1, msgCommandChannel.builder.pubSubIndex(), msgCommandChannel);
				}
				return result;
			} else {
				return FailableWrite.Retry;
			}
		}		
	}


	/**
     * Publishes specified topic with no data onto this channel
     * @return published topic if msgCommandChannel.goHasRoom
     */
	public boolean publishTopic() {
		assert((0 != (msgCommandChannel.initFeatures & MsgCommandChannel.DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
		
		int token =  token();
		
		if (token>=0) {
			return msgCommandChannel.publishOnPrivateTopic(token);
		} else {
			if (null==msgCommandChannel.messagePubSub) {
				if (msgCommandChannel.builder.isAllPrivateTopics()) {
					throw new RuntimeException("Discovered non private topic '"+topic+"' but exclusive use of private topics was set on.");
				} else {
					throw new RuntimeException("Enable DYNAMIC_MESSAGING for this CommandChannel before publishing.");
				}
			}
			
		    if (msgCommandChannel.goHasRoom()  && 
		    	PipeWriter.tryWriteFragment(msgCommandChannel.messagePubSub, MessagePubSub.MSG_PUBLISH_103)) {
				
				PipeWriter.writeInt(msgCommandChannel.messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_QOS_5, WaitFor.All.policy());
		    	
				DataOutputBlobWriter<MessagePubSub> output = PipeWriter.outputStream(msgCommandChannel.messagePubSub);
				output.openField();	    		
				output.write(topicBytes, 0, topicBytes.length);
				
				MsgCommandChannel.publicTrackedTopicSuffix(msgCommandChannel, output);
				
				output.closeHighLevelField(MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1);
				
				////OLD: PipeWriter.writeUTF8(messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1, topic);         
		
		    	
		    	
				PipeWriter.writeSpecialBytesPosAndLen(msgCommandChannel.messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_PAYLOAD_3, -1, 0);
				PipeWriter.publishWrites(msgCommandChannel.messagePubSub);
		
		        MsgCommandChannel.publishGo(1,msgCommandChannel.builder.pubSubIndex(), msgCommandChannel);
		                    
		        return true;
		        
		    } else {
		        return false;
		    }
		}
	}

	/**
     * Publishes specified topic with no data onto this channel while not accepting new messages until published message is received
	 * @param waitFor WaitFor arg used in PipeWriter.writeInt
     * @return published topic if msgCommandChannel.goHasRoom
     */
	public boolean publishTopic(WaitFor waitFor) {
		assert((0 != (msgCommandChannel.initFeatures & MsgCommandChannel.DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
		
		int token =  token();
		
		if (token>=0) {
			return msgCommandChannel.publishOnPrivateTopic(token);
		} else {
			if (null==msgCommandChannel.messagePubSub) {
				if (msgCommandChannel.builder.isAllPrivateTopics()) {
					throw new RuntimeException("Discovered non private topic '"+topic+"' but exclusive use of private topics was set on.");
				} else {
					throw new RuntimeException("Enable DYNAMIC_MESSAGING for this CommandChannel before publishing.");
				}
			}
			
		    if (msgCommandChannel.goHasRoom()  && 
		    	PipeWriter.tryWriteFragment(msgCommandChannel.messagePubSub, MessagePubSub.MSG_PUBLISH_103)) {
				
				PipeWriter.writeInt(msgCommandChannel.messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_QOS_5, waitFor.policy());
		    	
				DataOutputBlobWriter<MessagePubSub> output = PipeWriter.outputStream(msgCommandChannel.messagePubSub);
				output.openField();	    		
				output.write(topicBytes, 0, topicBytes.length);
				
				MsgCommandChannel.publicTrackedTopicSuffix(msgCommandChannel, output);
				
				output.closeHighLevelField(MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1);
				
				////OLD: PipeWriter.writeUTF8(messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1, topic);         
		
		    	
		    	
				PipeWriter.writeSpecialBytesPosAndLen(msgCommandChannel.messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_PAYLOAD_3, -1, 0);
				PipeWriter.publishWrites(msgCommandChannel.messagePubSub);
		
		        MsgCommandChannel.publishGo(1,msgCommandChannel.builder.pubSubIndex(), msgCommandChannel);
		                    
		        return true;
		        
		    } else {
		        return false;
		    }
		}
	}

	/**
	 * Publishes specified topic with data written onto this channel
     * @param writable to write data into this channel
	 * @return published topic if token >= 0
	 */
	public boolean publishTopic(Writable writable) {
		assert((0 != (msgCommandChannel.initFeatures & MsgCommandChannel.DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
		assert(writable != null);
		
		int token = token();		
		
		if (token >= 0) {
			return msgCommandChannel.publishOnPrivateTopic(token, writable);
		} else {
			
			//if messagePubSub is null then this is a private topic but why is publishPrivateTopics null?
			assert(null != msgCommandChannel.messagePubSub) : "pipe must not be null, topic: "+topic+" has privateTopicsPub:"+msgCommandChannel.publishPrivateTopics+"\n   cmd: "+msgCommandChannel.hashCode();
		    
			if (msgCommandChannel.goHasRoom()  && 
		    	PipeWriter.tryWriteFragment(msgCommandChannel.messagePubSub, MessagePubSub.MSG_PUBLISH_103)) {
				
				PipeWriter.writeInt(msgCommandChannel.messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_QOS_5, WaitFor.All.policy());
		    	//PipeWriter.writeUTF8(messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1, topic);         
		
		    	DataOutputBlobWriter<MessagePubSub> output = PipeWriter.outputStream(msgCommandChannel.messagePubSub);
		 		output.openField();	    		
		 		output.write(topicBytes, 0, topicBytes.length);     		
		 		MsgCommandChannel.publicTrackedTopicSuffix(msgCommandChannel, output);
		 		output.closeHighLevelField(MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1);
		 		
		        PubSubWriter pw = (PubSubWriter) Pipe.outputStream(msgCommandChannel.messagePubSub);	           
		    	DataOutputBlobWriter.openField(pw);
		    	writable.write(pw);
		        DataOutputBlobWriter.closeHighLevelField(pw, MessagePubSub.MSG_PUBLISH_103_FIELD_PAYLOAD_3);
		        
		        PipeWriter.publishWrites(msgCommandChannel.messagePubSub);
		
		        MsgCommandChannel.publishGo(1,msgCommandChannel.builder.pubSubIndex(), msgCommandChannel);
		                    
		        
		        return true;
		        
		    } else {
		        return false;
		    }
		}
	}

	/**
     * Publishes specified topic with data written onto this channel while not accepting new messages until published message is received
     * @param writable to write data into this channel
	 * @param waitFor waitFor arg used in PipeWriter.writeInt
	 * @return published topic if token >= 0
	 */
	public boolean publishTopic(Writable writable, WaitFor waitFor) {
		assert((0 != (msgCommandChannel.initFeatures & MsgCommandChannel.DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
		assert(writable != null);
		
		int token = token();
		
		if (token>=0) {
			return msgCommandChannel.publishOnPrivateTopic(token, writable);
		} else {
		    if (msgCommandChannel.goHasRoom() && 
		    	PipeWriter.tryWriteFragment(msgCommandChannel.messagePubSub, MessagePubSub.MSG_PUBLISH_103)) {
				
				PipeWriter.writeInt(msgCommandChannel.messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_QOS_5, waitFor.policy());
		    	//PipeWriter.writeUTF8(messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1, topic);         
		
		    	DataOutputBlobWriter<MessagePubSub> output = PipeWriter.outputStream(msgCommandChannel.messagePubSub);
		 		output.openField();	    		
		 		output.write(topicBytes, 0, topicBytes.length);     		
		 		MsgCommandChannel.publicTrackedTopicSuffix(msgCommandChannel, output);
		 		output.closeHighLevelField(MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1);
		 		
		        PubSubWriter pw = (PubSubWriter) Pipe.outputStream(msgCommandChannel.messagePubSub);	           
		    	DataOutputBlobWriter.openField(pw);
		    	writable.write(pw);
		        DataOutputBlobWriter.closeHighLevelField(pw, MessagePubSub.MSG_PUBLISH_103_FIELD_PAYLOAD_3);
		        
		        PipeWriter.publishWrites(msgCommandChannel.messagePubSub);
		
		        MsgCommandChannel.publishGo(1, msgCommandChannel.builder.pubSubIndex(), msgCommandChannel);
		                    
		        
		        return true;
		        
		    } else {
		        return false;
		    }
		}
	}
		
	public void presumePublishTopic(Writable writable) {
		presumePublishTopic(writable, WaitFor.All);
	}

	public void presumePublishTopic(Writable writable, WaitFor waitFor) {
		assert((0 != (msgCommandChannel.initFeatures & MsgCommandChannel.DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
		
		if (!publishTopic(writable, waitFor)) {
			logger.warn("unable to publish on topic {} must wait.",topic);
			while (!publishTopic(writable, waitFor)) {
				Thread.yield();
			}
		}
	}


	
	/**
	 * start shutdown of the runtime, this can be vetoed or postponed by any shutdown listeners
	 */
	public void requestShutdown() {
		
		assert(msgCommandChannel.enterBlockOk()) : "Concurrent usage error, ensure this never called concurrently";
		try {
			msgCommandChannel.builder.requestShutdown();
		} finally {
		    assert(msgCommandChannel.exitBlockOk()) : "Concurrent usage error, ensure this never called concurrently";      
		}
	}

	/**
	 * Used to set delay of service in nanoseconds
	 * @param durationNanos long duration in nanoseconds used to set delay time
	 * @return true if msgCommandChannel.goHasRoom else false
	 */
	public boolean delay(long durationNanos) {
		assert(msgCommandChannel.enterBlockOk()) : "Concurrent usage error, ensure this never called concurrently";
		try {
		    if (msgCommandChannel.goHasRoom()) {
		    	MsgCommandChannel.publishBlockChannel(durationNanos, msgCommandChannel);
		        return true;
		    } else {
		        return false;
		    }
		} finally {
		    assert(msgCommandChannel.exitBlockOk()) : "Concurrent usage error, ensure this never called concurrently";      
		}
	}

	/**
	 * Used to set delay of service until some action takes place
	 * @param msTime long duration in milliseconds used to set delay time
	 * @return true if msgCommandChannel.goHasRoom else false
	 */
	public boolean delayUntil(long msTime) {
		assert(msgCommandChannel.enterBlockOk()) : "Concurrent usage error, ensure this never called concurrently";
		try {
		    if (msgCommandChannel.goHasRoom()) {
		    	MsgCommandChannel.publishBlockChannelUntil(msTime, msgCommandChannel);
		        return true;
		    } else {
		        return false;
		    }
		} finally {
		    assert(msgCommandChannel.exitBlockOk()) : "Concurrent usage error, ensure this never called concurrently";      
		}
		
	}
	
}
