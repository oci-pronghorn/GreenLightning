package com.ociweb.gl.impl.stage;

import static com.ociweb.pronghorn.pipe.Pipe.blobMask;
import static com.ociweb.pronghorn.pipe.Pipe.byteBackingArray;
import static com.ociweb.pronghorn.pipe.Pipe.bytePosition;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.gl.api.WaitFor;
import com.ociweb.gl.impl.BuilderImpl;
import com.ociweb.gl.impl.schema.IngressMessages;
import com.ociweb.gl.impl.schema.MessagePubSub;
import com.ociweb.gl.impl.schema.MessageSubscription;
import com.ociweb.gl.impl.schema.TrafficAckSchema;
import com.ociweb.gl.impl.schema.TrafficReleaseSchema;
import com.ociweb.pronghorn.pipe.DataOutputBlobWriter;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeReader;
import com.ociweb.pronghorn.pipe.RawDataSchema;
import com.ociweb.pronghorn.pipe.util.hash.IntHashTable;
import com.ociweb.pronghorn.stage.PronghornStage;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;
import com.ociweb.pronghorn.util.Appendables;
import com.ociweb.pronghorn.util.TrieParser;
import com.ociweb.pronghorn.util.TrieParserReader;
import com.ociweb.pronghorn.util.TrieParserReaderLocal;

public class MessagePubSubImpl {
	

	//TODO: if we have NO traffic cop usages then we should choose pub sub without AbstractTrafficOrderedStage
	
	//TODO: convert go to use low level 	
	//TODO: 1 copy subscriber must hold offset and length not LOC...
	//TODO: need abstract base using low level
	//TODO: need pub sub not using abstract base
	
	//TODO: need wild card support.
	
	//TODO: is block support here on go pipe, without go pipe? how
	
	//TODO: recommend the use of private topics when found
	
	enum PubType {
		Message, State;
	}

	public Pipe<IngressMessages>[] ingressMessagePipes;
	public Pipe<MessagePubSub>[] incomingSubsAndPubsPipe;
	public Pipe<MessageSubscription>[] outgoingMessagePipes;
	public int subscriberListSize;
	public int[] subscriberLists;
	public int totalSubscriberLists;
	public TrieParser localSubscriptionTrie;
	public TrieParserReader localSubscriptionTrieReader;
	public IntHashTable subscriptionPipeLookup;
	public IntHashTable deDupeTable;
	public boolean pendingIngress;
	public int[] pendingPublish;
	public long[][] consumedMarks;
	public boolean[] pendingAck;
	public int[] requiredConsumes;
	public byte[] topicBacking;
	public int topicPos;
	public int topicLen;
	public int topicMask;
	public byte[] payloadBacking;
	public int payloadPos;
	public int payloadLen;
	public int payloadMask;
	public MessagePubSubImpl.PubType pendingDeliveryType;
	public int pendingPublishCount;
	public int pendingReleaseCountIdx;
	public TrieParser topicConversionTrie;
	public int currentState;
	public int newState;
	public int stateChangeInFlight;
	public MessagePubSubTrace pubSubTrace;
	public Pipe<RawDataSchema> tempSubject;
	public String graphName;
	public boolean foundWork;
	public CollectTargetLists visitor;
	//TODO: if on watch for special $ topic to turn it on for specific topics..
	static final boolean enableTrace = false;
	public static final int initialSubscriptions = 64; //Will grow as needed.
	public static final int estimatedAvgTopicLength = 128; //will grow as needed    
	public static final byte[] WILD_POUND_THE_END = "/%b".getBytes();
	public static final byte[] WILD_PLUS_THE_SEGMENT = "%b/".getBytes();
	public final static boolean showNewSubscriptions = false;
	final static Logger logger = LoggerFactory.getLogger(MessagePubSubImpl.class);

	public MessagePubSubImpl(boolean pendingIngress, int stateChangeInFlight, MessagePubSubTrace pubSubTrace) {
		this.pendingIngress = pendingIngress;
		this.stateChangeInFlight = stateChangeInFlight;
		this.pubSubTrace = pubSubTrace;
	}

	void copyToSubscriberState(int oldOrdinal, int newOrdinal, int pipeIdx, long[] targetMarks) {
	    Pipe<MessageSubscription> outPipe = outgoingMessagePipes[pipeIdx];
	    if (Pipe.hasRoomForWrite(outPipe)) {
	    	int size = Pipe.addMsgIdx(outPipe, MessageSubscription.MSG_STATECHANGED_71);
	    	assert(oldOrdinal != newOrdinal) : "Stage change must actualt change the state!";
	    	Pipe.addIntValue(oldOrdinal, outPipe);
	    	Pipe.addIntValue(newOrdinal, outPipe);
	 
	        //due to batching this may not become the head position upon publish but it will do so eventually.
	        //so to track this position we use workingHeadPosition not headPosition
	    	targetMarks[pipeIdx] = Pipe.workingHeadPosition(outPipe);
	
	    	Pipe.confirmLowLevelWrite(outPipe, size);
	    	Pipe.publishWrites(outPipe);
	    } else {
	    	pendingPublish[pendingPublishCount++] = pipeIdx;
	    	pendingIngress = false;
	    }
	}

	void copyToSubscriber(
			int pipeIdx, long[] targetMarks, byte[] topicBacking, int topicPos, int topicLength, int topicMask, byte[] payloadBacking, int payloadPos, int payloadLength, int payloadMask
	
			) {
	
	    Pipe<MessageSubscription> outPipe = outgoingMessagePipes[pipeIdx];
	    if (Pipe.hasRoomForWrite(outPipe)) {
	    	int size = Pipe.addMsgIdx(outPipe, MessageSubscription.MSG_PUBLISH_103);
	
	    	Pipe.addByteArray(topicBacking, topicPos, topicLength, topicMask, outPipe);
	
	    	if (payloadLength>0) {
	    		Pipe.addByteArray(payloadBacking, payloadPos, payloadLength, payloadMask, outPipe); 	
	    	} else {
	    		Pipe.addNullByteArray(outPipe);
	    	}
	 
	        //due to batching this may not become the head position upon publish but it will do so eventually.
	        //so to track this position we use workingHeadPosition not headPosition
	    	if (null!=targetMarks) {
	    		targetMarks[pipeIdx] = Pipe.workingHeadPosition(outPipe);
	    	}
	    	Pipe.confirmLowLevelWrite(outPipe, size);
	    	Pipe.publishWrites(outPipe);
	    	
	    } else {
	    	//add this one back to the list so we can send again later
	      	pendingPublish[pendingPublishCount++] = pipeIdx; 
	        pendingIngress = (null==targetMarks); //true for ingress and false for normal
	    }
	}

	void convertMQTTTopicsToLocal(final byte[] backing1, final int pos1, final int len1, final int mask1) {
		int size = Pipe.addMsgIdx(tempSubject, RawDataSchema.MSG_CHUNKEDSTREAM_1);
		DataOutputBlobWriter<RawDataSchema> stream = Pipe.outputStream(tempSubject);
		DataOutputBlobWriter.openField(stream);
		TrieParserReader.parseSetup(TrieParserReaderLocal.get(), backing1, pos1, len1, mask1);
		boolean foundEnd = false;
		while (TrieParserReader.parseHasContent(TrieParserReaderLocal.get())) {
						
			if (foundEnd) {
				throw new UnsupportedOperationException("Invalid topic if /# is used it must only be at the end.");
			}
			
			int token = (int)TrieParserReader.parseNext(TrieParserReaderLocal.get(), topicConversionTrie);
			if (-1 == token) {				
				stream.write(TrieParserReader.parseSkipOne(TrieParserReaderLocal.get()));
			} if (1 == token) {
				stream.write(MessagePubSubImpl.WILD_POUND_THE_END); //  /#
				foundEnd = true;
			} if (2 == token) {
				stream.write(MessagePubSubImpl.WILD_PLUS_THE_SEGMENT); //  +/
			}
		}
		
		DataOutputBlobWriter.closeLowLevelField(stream);
		Pipe.confirmLowLevelWrite(tempSubject, size);
		Pipe.publishWrites(tempSubject);
	}

	int beginNewSubscriptionList(final byte[] backing1, final int pos1, final int len1, final int mask1, int len, int mask, int pos, byte[] backing) {
		int listIdx;
		if (MessagePubSubImpl.showNewSubscriptions) {
			MessagePubSubImpl.logger.info("adding new subscription {} as internal pattern {}",
					Appendables.appendUTF8(new StringBuilder(), backing1, pos1, len1, mask1),
					Appendables.appendUTF8(new StringBuilder(), backing, pos, len, mask));
		}
		
		//create new subscription
		listIdx = subscriberListSize*totalSubscriberLists++;
		
		if (listIdx+subscriberListSize >= subscriberLists.length) {
			//grow the list			
			int i = subscriberLists.length;
			int[] temp = new int[i*2];
			Arrays.fill(temp, -1);
			System.arraycopy(subscriberLists, 0, temp, 0, i);
			subscriberLists = temp;
		}
		
		subscriberLists[listIdx] = 0;
		//System.err.println("Adding new subscription with value "+listIdx);
		localSubscriptionTrie.setValue(backing, pos, len, mask, listIdx);
		return listIdx;
	}

	void addSubscription(final short pipeIdx, final byte[] backing1, final int pos1, final int len1, final int mask1) {
	
	    //////////////////////
		//convert # and + to support escape values
		//////////////////////
		convertMQTTTopicsToLocal(backing1, pos1, len1, mask1);
		
		int msgIdx = Pipe.takeMsgIdx(tempSubject);
		int mta = Pipe.takeByteArrayMetaData(tempSubject);
		int len = Pipe.takeByteArrayLength(tempSubject);
		int mask = blobMask(tempSubject);	
		int pos = bytePosition(mta, tempSubject, len)&mask;     		
		byte[] backing = byteBackingArray(mta, tempSubject);
		
		Pipe.confirmLowLevelRead(tempSubject, Pipe.sizeOf(RawDataSchema.instance, RawDataSchema.MSG_CHUNKEDSTREAM_1));
		Pipe.releaseReadLock(tempSubject);
	    //////////////////////////////
		//finished conversion of # and +
		//////////////////////////////
		
		//Appendables.appendUTF8(System.err.append("add subscription:"), backing, pos, len, mask);
		//System.err.println("");
		
		int listIdx = subscriptionListIdx(backing, pos, len, mask);
		
		if (listIdx<0) {
			listIdx = beginNewSubscriptionList(backing1, pos1, len1, mask1, len, mask, pos, backing);	
		}
		
		//add index on first -1 or stop if value already found  
		
		int length = subscriberLists[listIdx];		
		int limit = 1+listIdx+length;
		
		for(int i = 1+listIdx; i<limit; i++) {
		    if (pipeIdx == subscriberLists[i]){
		        return;//already in list.
		    }
		}
		subscriberLists[limit]=pipeIdx; //outgoing pipe which will be sent this data.
		subscriberLists[listIdx]++;
		
	}

	void unsubscribe(short pipeIdx, byte[] backing1, int pos1, int len1, int mask1) {
	
	    //////////////////////
		//convert # and + to support escape values
		//////////////////////
		convertMQTTTopicsToLocal(backing1, pos1, len1, mask1);
		
		int msgIdx = Pipe.takeMsgIdx(tempSubject);
		int mta = Pipe.takeByteArrayMetaData(tempSubject);
		int len = Pipe.takeByteArrayLength(tempSubject);
		int mask = blobMask(tempSubject);	
		int pos = bytePosition(mta, tempSubject, len)&mask;     		
		byte[] backing = byteBackingArray(mta, tempSubject);
		
		Pipe.confirmLowLevelRead(tempSubject, Pipe.sizeOf(RawDataSchema.instance, RawDataSchema.MSG_CHUNKEDSTREAM_1));
		Pipe.releaseReadLock(tempSubject);
	    //////////////////////////////
		//finished conversion of # and +
		//////////////////////////////
				
		int listIdx = subscriptionListIdx(backing, pos, len, mask);
		
		if (listIdx >= 0) {
			
			int length = subscriberLists[listIdx];
			final int limit = 1+listIdx+length;
			
			//add index on first -1 or stop if value already found                    
			for(int i = listIdx+1; i<limit; i++) {
				
			    if (pipeIdx == subscriberLists[i]){
			    	//stops after the -1 is moved up.
			    	while ((i<(listIdx+subscriberListSize)) && (-1!=subscriberLists[i])) {
			    		subscriberLists[i] = subscriberLists[++i];
			    	}
			    	subscriberLists[listIdx]--;//remove 1 from count of subscribers;
			        break;
			    }
			}
		}
		
	
	}

	boolean isNotBlockedByStateChange(Pipe<MessagePubSub> pipe) {
		return (stateChangeInFlight == -1) ||
				( !PipeReader.peekMsg(pipe, MessagePubSub.MSG_CHANGESTATE_70));
	}

	int subscriptionListIdx(final byte[] backing, final int pos, final int len, final int mask) {
				
		return (int) TrieParserReader.query(localSubscriptionTrieReader, localSubscriptionTrie, backing, pos, len, mask);
	
	}

	void collectAllSubscriptionLists(final byte[] backing, final int pos, final int len, final int mask) {
		//right after all the subscribers use the rest of the data
		if (null != visitor) {
			visitor.reset(subscriberLists, subscriberListSize*totalSubscriberLists);
		} else {
			visitor = new CollectTargetLists(subscriberLists, subscriberListSize*totalSubscriberLists);
		}
		localSubscriptionTrieReader.visit(localSubscriptionTrie, visitor, backing, pos, len, mask);
		subscriberLists = visitor.targetArray(); //must assign back in case we made it grow.
	
	}

	void allPendingStateChanges(int limit, int[] localPP, long[] targetMakrs, int localCurState, int localNewState) {
		//finishing the remaining copies that could not be done before because the pipes were full
		for(int i = 0; i<limit; i++) {
			copyToSubscriberState(localCurState, localNewState, localPP[i], targetMakrs);                
		}
	}

	void allPendingIngressMessages(int startIdx, int limit, int[] localPP, Pipe<IngressMessages> pipe) {
	
		for(int i = startIdx; i<limit; i++) {
			copyToSubscriber(
					localPP[i], null, topicBacking,
					 topicPos, topicLen, topicMask, payloadBacking,
					 payloadPos, payloadLen, payloadMask
					);                
		}
	}

	void allPendingNormalMessages(int startIdx, int limit, int[] localPP, long[] targetMakrs, Pipe<MessagePubSub> pipe) {
		
		for(int i = startIdx; i<limit; i++) {			        			
			copyToSubscriber(
					 localPP[i], targetMakrs, topicBacking, 
					 topicPos, topicLen, topicMask, payloadBacking,
					 payloadPos, payloadLen, payloadMask
					);                
		}
	}

	void processPending() {
		int limit = pendingPublishCount;
		pendingPublishCount = 0;//set to zero to collect the new failed values
		foundWork=true;
		
		if (MessagePubSubImpl.PubType.Message == pendingDeliveryType) {
			if (pendingIngress) {		    			
				allPendingIngressMessages(0, limit, pendingPublish, ingressMessagePipes[pendingReleaseCountIdx]);
			} else {
				allPendingNormalMessages(0, limit, pendingPublish, consumedMarks[pendingReleaseCountIdx], incomingSubsAndPubsPipe[pendingReleaseCountIdx]);
			}
		} else if (MessagePubSubImpl.PubType.State == pendingDeliveryType) {
			assert(!pendingIngress);
			allPendingStateChanges(limit, pendingPublish, consumedMarks[pendingReleaseCountIdx], currentState, newState);
			
			if (0 == pendingPublishCount) {
				currentState = newState;
			}		
		}
	
	}

	void unsubscribe(Pipe<MessagePubSub> pipe) {
		int hash = PipeReader.readInt(pipe, MessagePubSub.MSG_UNSUBSCRIBE_101_FIELD_SUBSCRIBERIDENTITYHASH_4); 
		final short pipeIdx = (short)IntHashTable.getItem(subscriptionPipeLookup, hash);
	  
		assert(pipeIdx>=0) : "Must have valid pipe index";
		
		final byte[] backing = PipeReader.readBytesBackingArray(pipe, MessagePubSub.MSG_UNSUBSCRIBE_101_FIELD_TOPIC_1);
		final int pos = PipeReader.readBytesPosition(pipe, MessagePubSub.MSG_UNSUBSCRIBE_101_FIELD_TOPIC_1);
		final int len = PipeReader.readBytesLength(pipe, MessagePubSub.MSG_UNSUBSCRIBE_101_FIELD_TOPIC_1);
		final int mask = PipeReader.readBytesMask(pipe, MessagePubSub.MSG_UNSUBSCRIBE_101_FIELD_TOPIC_1);
		
		unsubscribe(pipeIdx, backing, pos, len, mask);
	
	}

	void addSubscription(Pipe<MessagePubSub> pipe) {
		
		//hash of the subscribing object
		int hash = PipeReader.readInt(pipe, MessagePubSub.MSG_SUBSCRIBE_100_FIELD_SUBSCRIBERIDENTITYHASH_4);
		//convert the hash into the specific outgoing pipe where this will go
		final short pipeIdx = (short)IntHashTable.getItem(subscriptionPipeLookup, hash);
		
		assert(pipeIdx>=0) : "Must have valid pipe index";
		
		final byte[] backing = PipeReader.readBytesBackingArray(pipe, MessagePubSub.MSG_SUBSCRIBE_100_FIELD_TOPIC_1);
		final int pos = PipeReader.readBytesPosition(pipe, MessagePubSub.MSG_SUBSCRIBE_100_FIELD_TOPIC_1);
		final int len = PipeReader.readBytesLength(pipe, MessagePubSub.MSG_SUBSCRIBE_100_FIELD_TOPIC_1);
		final int mask = PipeReader.readBytesMask(pipe, MessagePubSub.MSG_SUBSCRIBE_100_FIELD_TOPIC_1);
		
		addSubscription(pipeIdx, backing, pos, len, mask);
	
	}

	void processStartupSubscriptions(Pipe<MessagePubSub> pipe) {
		 
		if (null==pipe) {
			MessagePubSubImpl.logger.info("warning this stage was created to listen to pub/sub but there are no subscriptions to be routed. This may not be important if subscriptions are added at runtime.");
			//this may not be an error.
			return; //no subscriptions were added.
		}
		/////////////////////////
		//WARNING: none of these operations can use outgoing pipes, they are not started yet.
		//         This code can and does take in the startup pipe and sets up local(internal) state
		////////////////////////
		
		while (PipeReader.tryReadFragment(pipe)) {
	        
	        int msgIdx = PipeReader.getMsgIdx(pipe);
	       
	        switch (msgIdx)  {
	        	case MessagePubSub.MSG_CHANGESTATE_70:
	        		
	        		if (newState!=currentState) {
	        			throw new UnsupportedOperationException("On startup there can only be 1 initial state");
	        		}
	        		
	        		newState = PipeReader.readInt(pipe, MessagePubSub.MSG_CHANGESTATE_70_FIELD_ORDINAL_7);
	        				
	        		//NOTE: this is sent to all outgoing pipes, some may not want state but are only here for listening to particular topics.
	        		//      This might be improved in the future if needed by capturing the list of only those pipes connected to instances of StateChangeListeners.
	            	for(int i = 0; i<outgoingMessagePipes.length; i++) {
	            		pendingPublish[pendingPublishCount++] = i;
	            	}
	        		break;
	            case MessagePubSub.MSG_SUBSCRIBE_100:
	                  addSubscription(pipe);                                  
	                break;
	            default:                    
	            	 throw new UnsupportedOperationException("Can not do "+msgIdx+" on startup");    
	            
	        }            
	        PipeReader.releaseReadLock(pipe);
	
		}
	}

	int countUnconsumed(long[] marks, int totalUnconsumed) {
		int i = marks.length;
		while (--i>=0) {    		
			long mark = marks[i];
			//only check those that have been set.
			if (mark>0) {
				if (Pipe.tailPosition(outgoingMessagePipes[i])<mark) {    				
					//logger.info("not consumed yet {}<{}",Pipe.tailPosition(outgoingMessagePipes[i]),mark);
					totalUnconsumed++;
				} else {
					//logger.info("is consumed {}>={}",Pipe.tailPosition(outgoingMessagePipes[i]),mark);
					marks[i] = 0;//clear this, tail was moved past mark
				}
			}
		}
		return totalUnconsumed;
	}

	boolean validMatching(Pipe<TrafficReleaseSchema>[] goPipe, Pipe<TrafficAckSchema>[] ackPipe) {
		//for every go pipe we must have a matching ack
		assert(goPipe.length==ackPipe.length);
		int i = goPipe.length;
		while (--i>=0) {
			
			if (null != goPipe[i] && null==ackPipe[i]) {
				MessagePubSubImpl.logger.warn("found go pipe but no ack pipe at index {}",i);
				return false;
			}
			
			if (null == goPipe[i] && null!=ackPipe[i]) {
				MessagePubSubImpl.logger.warn("found ack pipe but no go pipe at index {}",i);
				return false;
			}    		
		}
		return true;
	}

	void startupPubSub(BuilderImpl hardware) {
		tempSubject.initBuffers();
	    
	    int incomingPipeCount = incomingSubsAndPubsPipe.length;
	    //for each pipe we must keep track of the consumed marks before sending the ack back
	    int outgoingPipeCount = outgoingMessagePipes.length;
	    consumedMarks = new long[incomingPipeCount][outgoingPipeCount];        
	    pendingAck = new boolean[incomingPipeCount];
	    requiredConsumes = new int[incomingPipeCount];
	    
	    //maximum count of outgoing pipes * 2 for extra hash room
	    deDupeTable = new IntHashTable(IntHashTable.computeBits(outgoingPipeCount*2));
	    
	    subscriberLists = new int[MessagePubSubImpl.initialSubscriptions*subscriberListSize];   
	    
	    Arrays.fill(subscriberLists, (short)-1);
	    localSubscriptionTrie = new TrieParser(MessagePubSubImpl.initialSubscriptions * MessagePubSubImpl.estimatedAvgTopicLength,1,false,true);//must support extraction for wild cards.
	
	    //this reader is set up for complete text only, all topics are sent in complete.
	    localSubscriptionTrieReader = new TrieParserReader(true);
	
	    pendingPublish = new int[subscriberListSize];
	    
	    processStartupSubscriptions(hardware.consumeStartupSubscriptions());
	}

	void readIngress(Pipe<IngressMessages> ingessPipe, int listIdx) {
		int length = subscriberLists[listIdx];
		if (length>0) {
		    
			if (MessagePubSubImpl.enableTrace) {
				pubSubTrace.init(ingessPipe, IngressMessages.MSG_PUBLISH_103_FIELD_TOPIC_1, 
											IngressMessages.MSG_PUBLISH_103_FIELD_PAYLOAD_3);
				MessagePubSubImpl.logger.info("new message to be routed, {}", pubSubTrace); 
			}
					 
			
		    topicBacking = PipeReader.readBytesBackingArray(ingessPipe, IngressMessages.MSG_PUBLISH_103_FIELD_TOPIC_1);
		    topicPos = PipeReader.readBytesPosition(ingessPipe, IngressMessages.MSG_PUBLISH_103_FIELD_TOPIC_1);
		    topicLen = PipeReader.readBytesLength(ingessPipe, IngressMessages.MSG_PUBLISH_103_FIELD_TOPIC_1);
		    topicMask = PipeReader.readBytesMask(ingessPipe, IngressMessages.MSG_PUBLISH_103_FIELD_TOPIC_1);                  
		    
		    payloadBacking = PipeReader.readBytesBackingArray(ingessPipe, IngressMessages.MSG_PUBLISH_103_FIELD_PAYLOAD_3);
		    payloadPos = PipeReader.readBytesPosition(ingessPipe, IngressMessages.MSG_PUBLISH_103_FIELD_PAYLOAD_3);
		    payloadLen = PipeReader.readBytesLength(ingessPipe, IngressMessages.MSG_PUBLISH_103_FIELD_PAYLOAD_3);
		    payloadMask = PipeReader.readBytesMask(ingessPipe, IngressMessages.MSG_PUBLISH_103_FIELD_PAYLOAD_3);                  
		         			
		    
			allPendingIngressMessages(listIdx+1, 1+listIdx+length, subscriberLists, ingessPipe);
		}
	}

	int ingressCollectSubLists(Pipe<IngressMessages> ingessPipe) {
		foundWork=true;
		final byte[] backing = PipeReader.readBytesBackingArray(ingessPipe, IngressMessages.MSG_PUBLISH_103_FIELD_TOPIC_1);
		final int pos = PipeReader.readBytesPosition(ingessPipe, IngressMessages.MSG_PUBLISH_103_FIELD_TOPIC_1);
		final int len = PipeReader.readBytesLength(ingessPipe, IngressMessages.MSG_PUBLISH_103_FIELD_TOPIC_1);
		final int mask = PipeReader.readBytesMask(ingessPipe, IngressMessages.MSG_PUBLISH_103_FIELD_TOPIC_1);
		int listIdx = subscriptionListIdx(backing, pos, len, mask);
		
		collectAllSubscriptionLists(backing, pos, len, mask);
		return listIdx;
	}

	void readNormalMessages(int a, Pipe<MessagePubSub> pipe, long[] targetMakrs, int ackPolicy, int listIdx) {
		int length = subscriberLists[listIdx];
		if (length>0) {
			
			if (MessagePubSubImpl.enableTrace) {        
				pubSubTrace.init(pipe, MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1, 
					         MessagePubSub.MSG_PUBLISH_103_FIELD_PAYLOAD_3);
				//logger.info("new message to be routed, {}", pubSubTrace);
			}
				                
			//////////
			//publish to all subscribers using wild card support here
			//TODO: walk the other lists
			///////////
	//	                        	int sumLen = 0;
	//	                    		int x = subscriberListSize*totalSubscriberLists;
	//	                    		while (subscriberLists[x]!=-1) {
	//	                    			final int idx = subscriberLists[x];
	//	                    			final int listLen = subscriberLists[idx];
	//	                    			final int listLim = 1+idx+listLen;
	//	                    			sumLen += listLen;
	//	                    			
	//		                        	for(int i = idx+1; i<listLim; i++) {
	//		                        		copyToSubscriber(pipe, subscriberLists[i], targetMakrs,
	//			                        				MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1, 
	//			                        				MessagePubSub.MSG_PUBLISH_103_FIELD_PAYLOAD_3
	//		                        				);                                
	//		                        	}
	//	                    			x++;
	//	                    		}
			
	//                        		logger.info("single use this list "+listIdx);
	//                        		int x = subscriberListSize*totalSubscriberLists;
	//                        		while (subscriberLists[x]!=-1) {
	//                        			final int idx = subscriberLists[x];
	//                        			logger.info("wild card lists "+idx);
	//                        			x++;
	//                        		}
			
			
			///////////
			///////////
			///////////
			
		    topicBacking = PipeReader.readBytesBackingArray(pipe, MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1);
		    topicPos = PipeReader.readBytesPosition(pipe, MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1);
		    topicLen = PipeReader.readBytesLength(pipe, MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1);
		    topicMask = PipeReader.readBytesMask(pipe, MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1);                  
		    
		    payloadBacking = PipeReader.readBytesBackingArray(pipe, MessagePubSub.MSG_PUBLISH_103_FIELD_PAYLOAD_3);
		    payloadPos = PipeReader.readBytesPosition(pipe, MessagePubSub.MSG_PUBLISH_103_FIELD_PAYLOAD_3);
		    payloadLen = PipeReader.readBytesLength(pipe, MessagePubSub.MSG_PUBLISH_103_FIELD_PAYLOAD_3);
		    payloadMask = PipeReader.readBytesMask(pipe, MessagePubSub.MSG_PUBLISH_103_FIELD_PAYLOAD_3);                  
		                                    
			allPendingNormalMessages(listIdx+1, 1+listIdx+length, subscriberLists, targetMakrs, pipe);
	
			pendingAck[a] = true;
			requiredConsumes[a] = WaitFor.computeRequiredCount(ackPolicy, length);                   	
			
		}
	}

	void construct(PronghornStage stage, GraphManager gm, IntHashTable subscriptionPipeLookup, BuilderImpl hardware, Pipe<IngressMessages>[] ingressMessagePipes, Pipe<MessagePubSub>[] incomingSubsAndPubsPipe, Pipe<TrafficReleaseSchema>[] goPipe, Pipe<TrafficAckSchema>[] ackPipe, Pipe<MessageSubscription>[] outgoingMessagePipes) {
		this.ingressMessagePipes = ingressMessagePipes;
		   this.incomingSubsAndPubsPipe = incomingSubsAndPubsPipe;
		   this.outgoingMessagePipes = outgoingMessagePipes;
		   assert(PronghornStage.noNulls(incomingSubsAndPubsPipe));
		   assert(goPipe.length == ackPipe.length) : "should be one ack pipe for every go pipe";
		   assert(goPipe.length == incomingSubsAndPubsPipe.length) : "Publish/Subscribe should be one pub sub pipe for every go "+goPipe.length+" vs "+incomingSubsAndPubsPipe.length;
	   
		   
		   //can never have more subscribers than ALL, add 1 for the leading counter and 1 for the -1 stop
		   subscriberListSize = outgoingMessagePipes.length+4;
		   
		   totalSubscriberLists = 0;
		   this.subscriptionPipeLookup = subscriptionPipeLookup;
	
		   currentState = null==hardware.beginningState ? -1 :hardware.beginningState.ordinal();

		   topicConversionTrie = new TrieParser(256,1,false,true,false);		
		   topicConversionTrie.setUTF8Value("/#", 1);  //   /%b     //from this point all wild card to end
		   topicConversionTrie.setUTF8Value("+/", 2);  //   %b/     //single level wild card up to next /
		      	   
		   
		   //TODO: need a better way to set when no subs are used except for startup only.
		   tempSubject = RawDataSchema.instance.newPipe(2, 0==incomingSubsAndPubsPipe.length ? MessagePubSubImpl.estimatedAvgTopicLength : incomingSubsAndPubsPipe[0].maxVarLen);
		  
		   GraphManager.addNota(gm, GraphManager.DOT_BACKGROUND, "gold2", stage);
		   GraphManager.addNota(gm, GraphManager.ROUTER_HUB, GraphManager.ROUTER_HUB, stage);
		   
		   //This extends traffic ordering and as a result often introduces a 2ms latency.
		   //until this is resolved this stage must be isolated
		   GraphManager.addNota(gm, GraphManager.ISOLATE, GraphManager.ISOLATE, stage);
		   graphName = gm.name;
	}
}