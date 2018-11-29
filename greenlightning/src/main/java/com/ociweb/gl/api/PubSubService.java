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

public class PubSubService {

	private static final Logger logger = LoggerFactory.getLogger(PubSubService.class);
	private final MsgCommandChannel<?> msgCommandChannel;
	
	public PubSubService(MsgCommandChannel<?> msgCommandChannel) {
		this.msgCommandChannel = msgCommandChannel;
		msgCommandChannel.initFeatures |= MsgCommandChannel.DYNAMIC_MESSAGING;
		msgCommandChannel.builder.messageRoutingRequired();
	}
	
	public PubSubService(MsgCommandChannel<?> msgCommandChannel,
								int queueLength, int maxMessageSize) {
		
		this.msgCommandChannel = msgCommandChannel;

		MsgCommandChannel.growCommandCountRoom(msgCommandChannel, queueLength);
		msgCommandChannel.initFeatures |= MsgCommandChannel.DYNAMIC_MESSAGING;  
		
		msgCommandChannel.pcm.ensureSize(MessagePubSub.class, queueLength, maxMessageSize);
		
		//also ensure consumers have pipes which can consume this.    		
		msgCommandChannel.pcm.ensureSize(MessageSubscription.class, queueLength, maxMessageSize);
		
		//IngressMessages Confirm that MQTT ingress is big enough as well			
		msgCommandChannel.pcm.ensureSize(IngressMessages.class, queueLength, maxMessageSize);
		
		msgCommandChannel.builder.messageRoutingRequired();
	}

	/**
	 * A method to determine if there is enough room in the pipe for more data
	 * @param messageCount int arg used in FieldReferenceOffsetManager.maxFragmentSize
	 * @return null==msgCommandChannel.goPipe || Pipe.hasRoomForWrite(msgCommandChannel.goPipe, FieldReferenceOffsetManager.maxFragmentSize(Pipe.from(msgCommandChannel.goPipe))*messageCount)
	 */
	public boolean hasRoomFor(CharSequence topic, int messageCount) {
		
		int token =  null==msgCommandChannel.publishPrivateTopics ? -1 : msgCommandChannel.publishPrivateTopics.getToken(topic);
		if (token>=0) {
			//private topics use their own pipe which must be checked.
			return Pipe.hasRoomForWrite(msgCommandChannel.publishPrivateTopics.getPipe(token), messageCount * Pipe.sizeOf(MessagePrivate.instance, MessagePrivate.MSG_PUBLISH_1));
		}
		
		return 
				(null==msgCommandChannel.goPipe || Pipe.hasRoomForWrite(msgCommandChannel.goPipe, FieldReferenceOffsetManager.maxFragmentSize(Pipe.from(msgCommandChannel.goPipe))*messageCount))
				&&
			    (Pipe.hasRoomForWrite(msgCommandChannel.messagePubSub, FieldReferenceOffsetManager.maxFragmentSize(Pipe.from(msgCommandChannel.messagePubSub))*messageCount));
		
	}

	
	public void logTelemetrySnapshot() {
		msgCommandChannel.logTelemetrySnapshot();
	}
	
	/**
	 * A method used to subscribe to a specified topic
	 * @param topic CharSequence arg used for output.append
	 * @return true if msgCommandChannel.goPipe == null || PipeWriter.hasRoomForWrite(msgCommandChannel.goPipe) <p> else false
	 */
	public boolean subscribe(CharSequence topic) {
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
			output.append(topic);
			
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
	 * @param topic CharSequence arg used in output.append
	 * @param listener PubSubMethodListenerBase arg used in PipeWriter.writeInt
	 * @return true if msgCommandChannel.goPipe == null || PipeWriter.hasRoomForWrite(msgCommandChannel.goPipe) <p> else false
	 */
	public boolean subscribe(CharSequence topic, PubSubMethodListenerBase listener) {
		assert((0 != (msgCommandChannel.initFeatures & MsgCommandChannel.DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
		
		if (msgCommandChannel.goHasRoom()  
			&& PipeWriter.tryWriteFragment(msgCommandChannel.messagePubSub, MessagePubSub.MSG_SUBSCRIBE_100)) {
		    
		    PipeWriter.writeInt(msgCommandChannel.messagePubSub, MessagePubSub.MSG_SUBSCRIBE_100_FIELD_SUBSCRIBERIDENTITYHASH_4, System.identityHashCode(listener));
		    //OLD -- PipeWriter.writeUTF8(messagePubSub, MessagePubSub.MSG_SUBSCRIBE_100_FIELD_TOPIC_1, topic);
		    
		    DataOutputBlobWriter<MessagePubSub> output = PipeWriter.outputStream(msgCommandChannel.messagePubSub);
			output.openField();	    		
			output.append(topic);
			
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
	 * @param topic CharSequence arg used in output.append
	 * @return true if msgCommandChannel.goPipe == null || PipeWriter.hasRoomForWrite(msgCommandChannel.goPipe) <p> else false
	 */
	public boolean unsubscribe(CharSequence topic) {
		assert((0 != (msgCommandChannel.initFeatures & MsgCommandChannel.DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
		
		assert((0 != (msgCommandChannel.initFeatures & MsgCommandChannel.DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
		
		if (msgCommandChannel.goHasRoom()  
			&& PipeWriter.tryWriteFragment(msgCommandChannel.messagePubSub, MessagePubSub.MSG_UNSUBSCRIBE_101)) {
		    
		    PipeWriter.writeInt(msgCommandChannel.messagePubSub, MessagePubSub.MSG_SUBSCRIBE_100_FIELD_SUBSCRIBERIDENTITYHASH_4, System.identityHashCode((PubSubMethodListenerBase)msgCommandChannel.listener));
		   //OLD  PipeWriter.writeUTF8(messagePubSub, MessagePubSub.MSG_UNSUBSCRIBE_101_FIELD_TOPIC_1, topic);
		    DataOutputBlobWriter<MessagePubSub> output = PipeWriter.outputStream(msgCommandChannel.messagePubSub);
			output.openField();	    		
			output.append(topic);
			
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
	 * @param topic CharSequence arg used in output.append
	 * @param listener PubSubMethodListenerBase arg used in PipeWriter.writeInt
	 * @return true if msgCommandChannel.goPipe == null || PipeWriter.hasRoomForWrite(msgCommandChannel.goPipe) <p> else false
	 */
	public boolean unsubscribe(CharSequence topic, PubSubMethodListenerBase listener) {
		assert((0 != (msgCommandChannel.initFeatures & MsgCommandChannel.DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
		
		if (msgCommandChannel.goHasRoom()  
			&& PipeWriter.tryWriteFragment(msgCommandChannel.messagePubSub, MessagePubSub.MSG_UNSUBSCRIBE_101)) {
		    
		    PipeWriter.writeInt(msgCommandChannel.messagePubSub, MessagePubSub.MSG_SUBSCRIBE_100_FIELD_SUBSCRIBERIDENTITYHASH_4, System.identityHashCode(listener));
		   //OLD  PipeWriter.writeUTF8(messagePubSub, MessagePubSub.MSG_UNSUBSCRIBE_101_FIELD_TOPIC_1, topic);
		    DataOutputBlobWriter<MessagePubSub> output = PipeWriter.outputStream(msgCommandChannel.messagePubSub);
			output.openField();	    		
			output.append(topic);
			
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
     * @param topic CharSequence arg to be specified for publish
     * @param writable to write data into this channel
     * @return result if msgCommandChannel.goHasRoom else FailableWrite.Retry
     */
	public FailableWrite publishFailableTopic(CharSequence topic, FailableWritable writable) {
		assert((0 != (msgCommandChannel.initFeatures & MsgCommandChannel.DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
		assert(writable != null);
		
		int token =  null==msgCommandChannel.publishPrivateTopics ? -1 : msgCommandChannel.publishPrivateTopics.getToken(topic);
		
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
		    		output.append(topic);
		    		
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
     * @param topic CharSequence arg to be specified for publish
     * @param writable to write data into this channel
	 * @param ap WaitFor arg used in PipeWriter.writeInt
	 * @return result if msgCommandChannel.goHasRoom else FailableWrite.Retry
	 */
	public FailableWrite publishFailableTopic(CharSequence topic, FailableWritable writable, WaitFor ap) {
		assert((0 != (msgCommandChannel.initFeatures & MsgCommandChannel.DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
		assert(writable != null);
		
		int token =  null==msgCommandChannel.publishPrivateTopics ? -1 : msgCommandChannel.publishPrivateTopics.getToken(topic);
		
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
		    		output.append(topic);
		    		
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
     * @param topic byte[] arg to be specified for publish
     * @return published topic if msgCommandChannel.goHasRoom
     */
	public boolean publishTopic(byte[] topic) {
		
		assert((0 != (msgCommandChannel.initFeatures & MsgCommandChannel.DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
		
		int token =  null==msgCommandChannel.publishPrivateTopics ? -1 : msgCommandChannel.publishPrivateTopics.getToken(topic, 0, topic.length);
		
		if (token>=0) {
			return msgCommandChannel.publishOnPrivateTopic(token);
		} else {
			//should not be called when	DYNAMIC_MESSAGING is not on.
			
		    //this is a public topic
			if (msgCommandChannel.goHasRoom()  && 
		    	PipeWriter.tryWriteFragment(msgCommandChannel.messagePubSub, MessagePubSub.MSG_PUBLISH_103)) {
		        
				DataOutputBlobWriter<MessagePubSub> output = PipeWriter.outputStream(msgCommandChannel.messagePubSub);
				output.openField();	
				output.write(topic);
				MsgCommandChannel.publicTrackedTopicSuffix(msgCommandChannel, output);	    		
				
				output.closeHighLevelField(MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1);
		    	//OLD  PipeWriter.writeBytes(messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1, topic);         
		    		        	
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
     * @param topic byte[] arg to be specified for publish
     * @param writable to write data into this channel
	 * @return published topic if msgCommandChannel.goHasRoom
     */
	public boolean publishTopic(byte[] topic, Writable writable) {
		
		assert((0 != (msgCommandChannel.initFeatures & MsgCommandChannel.DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
		assert(writable != null);
		
		int token =  null==msgCommandChannel.publishPrivateTopics ? -1 : msgCommandChannel.publishPrivateTopics.getToken(topic, 0, topic.length);
		
		if (token>=0) {
			return msgCommandChannel.publishOnPrivateTopic(token, writable);
		} else {
			//should not be called when	DYNAMIC_MESSAGING is not on.
			
		    //this is a public topic
			if (msgCommandChannel.goHasRoom()  && 
		    	PipeWriter.tryWriteFragment(msgCommandChannel.messagePubSub, MessagePubSub.MSG_PUBLISH_103)) {
		        
				DataOutputBlobWriter<MessagePubSub> output = PipeWriter.outputStream(msgCommandChannel.messagePubSub);
				output.openField();	
				output.write(topic);
				MsgCommandChannel.publicTrackedTopicSuffix(msgCommandChannel, output);    		
				
				output.closeHighLevelField(MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1);
				
		    	//OLD PipeWriter.writeBytes(messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1, topic);         
		    	
		        PubSubWriter writer = (PubSubWriter) Pipe.outputStream(msgCommandChannel.messagePubSub);
		        
		        writer.openField(MessagePubSub.MSG_PUBLISH_103_FIELD_PAYLOAD_3,msgCommandChannel);
		        writable.write(writer);
		        writer.publish();
		        MsgCommandChannel.publishGo(1,msgCommandChannel.builder.pubSubIndex(), msgCommandChannel);
		                    
		        return true;
		        
		    } else {
		        return false;
		    }
		}
	}

	/**
     * Publishes specified topic with no data onto this channel
     * @param topic CharSequence arg to be specified for publish
     * @return published topic if msgCommandChannel.goHasRoom
     */
	public boolean publishTopic(CharSequence topic) {
		
		assert((0 != (msgCommandChannel.initFeatures & MsgCommandChannel.DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
		
		int token =  null==msgCommandChannel.publishPrivateTopics ? -1 : msgCommandChannel.publishPrivateTopics.getToken(topic);
		
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
				output.append(topic);
				
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
     * @param topic CharSequence arg to be specified for publish
	 * @param waitFor WaitFor arg used in PipeWriter.writeInt
     * @return published topic if msgCommandChannel.goHasRoom
     */
	public boolean publishTopic(CharSequence topic, WaitFor waitFor) {
		
		assert((0 != (msgCommandChannel.initFeatures & MsgCommandChannel.DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
		
		int token =  null==msgCommandChannel.publishPrivateTopics ? -1 : msgCommandChannel.publishPrivateTopics.getToken(topic);
		
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
				output.append(topic);
				
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
     * @param topic CharSequence arg to be specified for publish
     * @param writable to write data into this channel
	 * @return published topic if token >= 0
	 */
	public boolean publishTopic(CharSequence topic, Writable writable) {
		
		assert((0 != (msgCommandChannel.initFeatures & MsgCommandChannel.DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
		assert(writable != null);
		
		
		///////////////////////////////////////////////////
		//hack test for now to see if this is worth doing.
		//NOTE: this is not helping much because HTTP header parsing dwarfs this work.
		int token;
		if (topic instanceof String) {
			if (topic == msgCommandChannel.cachedTopic) {
				token = msgCommandChannel.cachedTopicToken;
			} else {
				
				token =  null==msgCommandChannel.publishPrivateTopics ? -1 : msgCommandChannel.publishPrivateTopics.getToken(topic);
				msgCommandChannel.cachedTopic = (String)topic;
				msgCommandChannel.cachedTopicToken = token;
			}
		} else {
			token =  null==msgCommandChannel.publishPrivateTopics ? -1 : msgCommandChannel.publishPrivateTopics.getToken(topic);	
		}
		//////////////////////////
		
		
		if (token>=0) {
			return msgCommandChannel.publishOnPrivateTopic(token, writable);
		} else {
			
			assert(msgCommandChannel.messagePubSub!=null) : "public topic is missing pipe in pub sub service for topic "+topic+" has private topics "+msgCommandChannel.publishPrivateTopics;
			
		    if (msgCommandChannel.goHasRoom()  && 
		    	PipeWriter.tryWriteFragment(msgCommandChannel.messagePubSub, MessagePubSub.MSG_PUBLISH_103)) {
				
				PipeWriter.writeInt(msgCommandChannel.messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_QOS_5, WaitFor.All.policy());
		    	//PipeWriter.writeUTF8(messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1, topic);         
		
		    	DataOutputBlobWriter<MessagePubSub> output = PipeWriter.outputStream(msgCommandChannel.messagePubSub);
		 		output.openField();	    		
		 		output.append(topic);	     		
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
     * @param topic CharSequence arg to be specified for publish
     * @param writable to write data into this channel
	 * @param waitFor waitFor arg used in PipeWriter.writeInt
	 * @return published topic if token >= 0
	 */
	public boolean publishTopic(CharSequence topic, Writable writable, WaitFor waitFor) {
		
		assert((0 != (msgCommandChannel.initFeatures & MsgCommandChannel.DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
		assert(writable != null);
		
		
		///////////////////////////////////////////////////
		//hack test for now to see if this is worth doing.
		//NOTE: this is not helping much because HTTP header parsing dwarfs this work.
		int token;
		if (topic instanceof String) {
			if (topic == msgCommandChannel.cachedTopic) {
				token = msgCommandChannel.cachedTopicToken;
			} else {
				token =  null==msgCommandChannel.publishPrivateTopics ? -1 : msgCommandChannel.publishPrivateTopics.getToken(topic);
				msgCommandChannel.cachedTopic = (String)topic;
				msgCommandChannel.cachedTopicToken = token;
			}
		} else {
			token =  null==msgCommandChannel.publishPrivateTopics ? -1 : msgCommandChannel.publishPrivateTopics.getToken(topic);	
		}
		//////////////////////////
		
		
		if (token>=0) {
			return msgCommandChannel.publishOnPrivateTopic(token, writable);
		} else {
		    if (msgCommandChannel.goHasRoom() && 
		    	PipeWriter.tryWriteFragment(msgCommandChannel.messagePubSub, MessagePubSub.MSG_PUBLISH_103)) {
				
				PipeWriter.writeInt(msgCommandChannel.messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_QOS_5, waitFor.policy());
		    	//PipeWriter.writeUTF8(messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1, topic);         
		
		    	DataOutputBlobWriter<MessagePubSub> output = PipeWriter.outputStream(msgCommandChannel.messagePubSub);
		 		output.openField();	    		
		 		output.append(topic);	     		
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
		
	public void presumePublishTopic(CharSequence topic, Writable writable) {
		presumePublishTopic(topic,writable, WaitFor.All);
	}

	public void presumePublishTopic(CharSequence topic, Writable writable, WaitFor waitFor) {
		assert((0 != (msgCommandChannel.initFeatures & MsgCommandChannel.DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
		
		if (!publishTopic(topic, writable, waitFor)) {
			logger.warn("unable to publish on topic {} must wait.",topic);
			while (!publishTopic(topic, writable, waitFor)) {
				Thread.yield();
			}
		}
	}

    /**
     * Publishes specified topic with data written onto this channel
     * @param topic topic to be specified for publish
     * @param writable to write data into this channel
     * @return published topic
     */
	public boolean publishTopic(TopicWritable topic, Writable writable) {
	
		return publishTopic(topic, writable, WaitFor.All);
	}

	/**
     * Publishes specified topic with data written onto this channel while not accepting new messages until published message is received
     * @param topic CharSequence arg to be specified for publish
     * @param writable to write data into this channel
	 * @param ap WaitFor arg used in PipeWriter.writeInt
	 * @return published topic if token >= 0
	 */
	public boolean publishTopic(TopicWritable topic, Writable writable, WaitFor ap) {
		
		assert((0 != (msgCommandChannel.initFeatures & MsgCommandChannel.DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
		assert(writable != null);
		
		int token = msgCommandChannel.tokenForPrivateTopic(topic);
		
		if (token>=0) {
			return msgCommandChannel.publishOnPrivateTopic(token, writable);
		} else { 
		    if (msgCommandChannel.goHasRoom()  && 
		    	PipeWriter.tryWriteFragment(msgCommandChannel.messagePubSub, MessagePubSub.MSG_PUBLISH_103)) {
		    	
				
				PipeWriter.writeInt(msgCommandChannel.messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_QOS_5, ap.policy());
		
		        
		        PubSubWriter pw = (PubSubWriter) Pipe.outputStream(msgCommandChannel.messagePubSub);
		    	DataOutputBlobWriter.openField(pw);
		    	topic.write(pw);
		 		MsgCommandChannel.publicTrackedTopicSuffix(msgCommandChannel, pw);
		    	DataOutputBlobWriter.closeHighLevelField(pw, MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1);
		       
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

	public boolean publishTopic(TopicWritable topic) {
		
		return publishTopic(topic, WaitFor.All);
	}
	
	public boolean publishTopic(TopicWritable topic, WaitFor ap) {
		
		return publishTopic(topic, ap);
	}

    /**
     * Takes previous state and changes it to specified state
     * @param state state used to convert original
      */
	public <E extends Enum<E>> boolean changeStateTo(E state) {
		assert((0 != (msgCommandChannel.initFeatures & MsgCommandChannel.DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
		
		 assert(msgCommandChannel.builder.isValidState(state));
		 if (!msgCommandChannel.builder.isValidState(state)) {
			 throw new UnsupportedOperationException("no match "+state.getClass());
		 }
		
		 if (msgCommandChannel.goHasRoom()  
		     && PipeWriter.tryWriteFragment(msgCommandChannel.messagePubSub, MessagePubSub.MSG_CHANGESTATE_70)) {
		
			 PipeWriter.writeInt(msgCommandChannel.messagePubSub, MessagePubSub.MSG_CHANGESTATE_70_FIELD_ORDINAL_7,  state.ordinal());
		     PipeWriter.publishWrites(msgCommandChannel.messagePubSub);
		     
		     msgCommandChannel.builder.releasePubSubTraffic(1, msgCommandChannel);
			 return true;
		 }
		
		return false;
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

	
}
