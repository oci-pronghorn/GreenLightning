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
import com.ociweb.pronghorn.pipe.PipeWriter;
import com.ociweb.pronghorn.pipe.RawDataSchema;
import com.ociweb.pronghorn.pipe.util.hash.IntHashTable;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;
import com.ociweb.pronghorn.util.Appendables;
import com.ociweb.pronghorn.util.TrieParser;
import com.ociweb.pronghorn.util.TrieParserReader;

public class MessagePubSubStage extends AbstractTrafficOrderedStage {

	public final static boolean showNewSubscriptions = true;
	
	private static final byte[] WILD_PLUS_THE_SEGMENT = "%b/".getBytes();
	private static final byte[] WILD_POUND_THE_END = "/%b".getBytes();

	private final static Logger logger = LoggerFactory.getLogger(MessagePubSubStage.class);
	
	private final Pipe<IngressMessages>[] ingressMessagePipes;
    private final Pipe<MessagePubSub>[] incomingSubsAndPubsPipe;
    private final Pipe<MessageSubscription>[] outgoingMessagePipes;
    
    private static final int estimatedAvgTopicLength = 128; //will grow as needed    
    private static final int initialSubscriptions = 64; //Will grow as needed.
    
    private final int subscriberListSize;
    private int[] subscriberLists; //single list for all subscriptions at different indexes.
    private int totalSubscriberLists;
    
	    
    private TrieParser localSubscriptionTrie;
    private TrieParserReader localSubscriptionTrieReader;
        
    private IntHashTable subscriptionPipeLookup;
    
    private boolean pendingIngress = false;
    private int[] pendingPublish; //remaining pipes that this pending message must be published to
    private long[][] consumedMarks; //ack only is sent after every subscribers tail has passed these marks
    private boolean[] pendingAck; //this input needs an ack and should be sent once all consumed marks are cleared.
    private int[] requiredConsumes;
    
    enum PubType {
    	Message, State;
    }
    
    private PubType pendingDeliveryType;
        
    private int pendingPublishCount;
    private int pendingReleaseCountIdx;

    private final TrieParser topicConversionTrie;
    private final TrieParserReader topicConversionTrieReader;
    
    //global state.
    private int currentState;
    private int newState;
    private int stateChangeInFlight = -1;

    //TODO: if on watch for special $ topic to turn it on for specific topics..
    private boolean enableTrace = false;
	private MessagePubSubTrace pubSubTrace = new MessagePubSubTrace();
    

	private final Pipe<RawDataSchema> tempSubject;
	
	
    /**
     * Provides an eventually consistent state model of events.  It works in the same way as the larger universe.  If a supernova is observed by two planets they may
     * not know about it at the same moment but the first who observes it can not send a message to the second that would arrive before the second observes the
     * even themselves.
     * 
     * Ensures that every subscriber gets its correct published messages.  The publisher CommandChannel does not receive an Ack( will not continue) until 
     * all subscribers have consumed the message from the pipe.  Other command channels are free to send other messages at the same time.  We only need to ensure 
     * the sequential behavior relative to a single command channel.
     * 
     * State changes have the same behavior as other messages above plus they block ALL other command channels from starting a state change until the current
     * change is complete.  The ensures that the meta state of the system can only be in one of 3 states ( A, B, or  transitioning from A to B) at any given
     * moment of time no more than 2 states will ever be in play at once.  
     * 
     * If the pipes become full and an mesage/stateChange can not be added to all the required pipes then NOTHING else is done (no new message processing) until
     * the partial delivery can become 100% complete.  This is critical to ensure the proper ordering of events.  
     * 
     * 
     * 
     * 
     * @param gm
     * @param subscriptionPipeLookup
     * @param hardware
     * @param incomingSubsAndPubsPipe
     * @param goPipe
     * @param ackPipe
     * @param outgoingMessagePipes
     */
    
    public MessagePubSubStage(GraphManager gm, IntHashTable subscriptionPipeLookup, BuilderImpl hardware, 
    						  Pipe<IngressMessages>[] ingressMessagePipes,
    		                  Pipe<MessagePubSub>[] incomingSubsAndPubsPipe,
                              Pipe<TrafficReleaseSchema>[] goPipe,
                              Pipe<TrafficAckSchema>[] ackPipe, 
                              Pipe<MessageSubscription>[] outgoingMessagePipes) {
       super(gm, hardware, join(ingressMessagePipes,incomingSubsAndPubsPipe), goPipe, ackPipe, outgoingMessagePipes);

       this.ingressMessagePipes = ingressMessagePipes;
       this.incomingSubsAndPubsPipe = incomingSubsAndPubsPipe;
       this.outgoingMessagePipes = outgoingMessagePipes;
       assert(noNulls(incomingSubsAndPubsPipe));
       assert(noNulls(goPipe)) : "Go Pipe must not contain nulls";
       assert(noNulls(ackPipe));
       assert(goPipe.length == ackPipe.length) : "should be one ack pipe for every go pipe";
        
       assert(goPipe.length == incomingSubsAndPubsPipe.length) : "Publish/Subscribe should be one pub sub pipe for every go "+goPipe.length+" vs "+incomingSubsAndPubsPipe.length;
       
       //can never have more subscribers than ALL, add 1 for the leading counter and 1 for the -1 stop
       this.subscriberListSize = outgoingMessagePipes.length+2;
       
       this.totalSubscriberLists = 0;
       this.subscriptionPipeLookup = subscriptionPipeLookup;

       this.currentState = null==hardware.beginningState ? -1 :hardware.beginningState.ordinal();

       supportsBatchedPublish = false; //must have immediate publish
   	   supportsBatchedRelease = false; //quick release is desirable to lower latency
   	   GraphManager.addNota(gm, GraphManager.SCHEDULE_RATE, 4_800, this);
   	   		
	   topicConversionTrie = new TrieParser(256,1,false,true,false);		
	   topicConversionTrie.setUTF8Value("/#", 1);  //   /%b     //from this point all wild card to end
	   topicConversionTrie.setUTF8Value("+/", 2);  //   %b/     //single level wild card up to next /
   	      	   
	   int maxCapturedFields = 16;
	   topicConversionTrieReader = new TrieParserReader(maxCapturedFields,true);
	   
	   tempSubject = RawDataSchema.instance.newPipe(2, incomingSubsAndPubsPipe[0].maxVarLen);
	   
    }

    
    private boolean isPreviousConsumed(int incomingPipeId) {
        	
    	long[] marks = consumedMarks[incomingPipeId];
    	int totalUnconsumed = 0;
    	
    	totalUnconsumed = countUnconsumed(marks, totalUnconsumed);

    	int totalConsumers = marks.length;
    	if ( (totalConsumers-totalUnconsumed) < requiredConsumes[incomingPipeId]  ) {
    		return false;
    	}
    	
    	if (totalUnconsumed>0) {
    		Arrays.fill(marks, 0);
    	}
    	sendAndClear(incomingPipeId);
    	
    	return true;//consumer has moved tail past all marks
    	
    }


	private int countUnconsumed(long[] marks, int totalUnconsumed) {
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


	private void sendAndClear(int incomingPipeId) {
		//if this is waiting for an ack send it and clear the value
    	if (pendingAck[incomingPipeId]) {   
   			PipeReader.releaseReadLock( incomingSubsAndPubsPipe[incomingPipeId]); 
   		    		
            decReleaseCount(incomingPipeId);    		
    		
            pendingAck[incomingPipeId] = false;
    		
    		//if this ack was for the state change in flight clear it
    		if (stateChangeInFlight == incomingPipeId) {
    			stateChangeInFlight = -1;
    		}
    	}
	}
    
    
    @Override
    public void startup() {
        super.startup();
        
        tempSubject.initBuffers();
        
        int incomingPipeCount = incomingSubsAndPubsPipe.length;
        //for each pipe we must keep track of the consumed marks before sending the ack back
        int outgoingPipeCount = outgoingMessagePipes.length;
        consumedMarks = new long[incomingPipeCount][outgoingPipeCount];        
        pendingAck = new boolean[incomingPipeCount];
        requiredConsumes = new int[incomingPipeCount];
        
        this.subscriberLists = new int[initialSubscriptions*subscriberListSize];   
        
        Arrays.fill(this.subscriberLists, (short)-1);
        this.localSubscriptionTrie = new TrieParser(initialSubscriptions * estimatedAvgTopicLength,1,false,true);//must support extraction for wild cards.

        //this reader is set up for complete text only, all topics are sent in complete.
        this.localSubscriptionTrieReader = new TrieParserReader(2,true);

        this.pendingPublish = new int[subscriberListSize];
        
        processStartupSubscriptions(hardware.consumeStartupSubscriptions());      
 
    }
    
    private void processStartupSubscriptions(Pipe<MessagePubSub> pipe) {
    	 
    	if (null==pipe) {
    		logger.info("warning this stage was created but there are no subscriptions to be routed.");
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

	private void addSubscription(Pipe<MessagePubSub> pipe) {
		
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


	private void unsubscribe(Pipe<MessagePubSub> pipe) {
		int hash = PipeReader.readInt(pipe, MessagePubSub.MSG_UNSUBSCRIBE_101_FIELD_SUBSCRIBERIDENTITYHASH_4); 
		final short pipeIdx = (short)IntHashTable.getItem(subscriptionPipeLookup, hash);
  
		assert(pipeIdx>=0) : "Must have valid pipe index";
		
		final byte[] backing = PipeReader.readBytesBackingArray(pipe, MessagePubSub.MSG_UNSUBSCRIBE_101_FIELD_TOPIC_1);
		final int pos = PipeReader.readBytesPosition(pipe, MessagePubSub.MSG_UNSUBSCRIBE_101_FIELD_TOPIC_1);
		final int len = PipeReader.readBytesLength(pipe, MessagePubSub.MSG_UNSUBSCRIBE_101_FIELD_TOPIC_1);
		final int mask = PipeReader.readBytesMask(pipe, MessagePubSub.MSG_UNSUBSCRIBE_101_FIELD_TOPIC_1);
		
		unsubscribe(pipeIdx, backing, pos, len, mask);

	}
	
	

	private boolean foundWork;
	
    @Override
    public void run() {

    	do {
    		foundWork = false;
	    	//////////////////////
	    	//process the pending publications, this must be completed before we continue
	    	//////////////////////
	        if (pendingPublishCount>0) { //must do these first.
	        	int limit = pendingPublishCount;
	        	pendingPublishCount = 0;//set to zero to collect the new failed values
	        	foundWork=true;
	        	switch(pendingDeliveryType) {
		        	case Message:
			        	{
			        		if (pendingIngress) {
			        			
			        			Pipe<IngressMessages> pipe = ingressMessagePipes[pendingReleaseCountIdx];
	
				        		for(int i = 0; i<limit; i++) {
				        			copyToSubscriber(pipe, pendingPublish[i],
				        					IngressMessages.MSG_PUBLISH_103_FIELD_TOPIC_1, 
				        					IngressMessages.MSG_PUBLISH_103_FIELD_PAYLOAD_3);                
				        		}
			        			
			        		} else {
				            	long[] targetMakrs = consumedMarks[pendingReleaseCountIdx];		           	 	
				        		Pipe<MessagePubSub> pipe = incomingSubsAndPubsPipe[pendingReleaseCountIdx];
	
				        		for(int i = 0; i<limit; i++) {			        			
				        			copyToSubscriber(pipe, pendingPublish[i], targetMakrs, 
				        					 MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1, 
				        					 MessagePubSub.MSG_PUBLISH_103_FIELD_PAYLOAD_3);                
				        		}
			        		}
	
			        	}
		        		break;
		        	case State:
			        	{
			        		assert(!pendingIngress);
			            	long[] targetMakrs = consumedMarks[pendingReleaseCountIdx];
			           	 
			        		//finishing the remaining copies that could not be done before because the pipes were full
			        		for(int i = 0; i<limit; i++) {
			        			copyToSubscriberState(currentState, newState, pendingPublish[i], targetMakrs);                
			        		}
		
			        		if (0 == pendingPublishCount) {
			        			currentState = newState;
			        		}
			        	}
		        		break;
	        	}
	            if (pendingPublishCount>0) {
	            	foundWork = false;
	            	//do not pick up new work until this is done or we may get out of order messages.
	                return;//try again later
	            } else {
	            	if (pendingIngress) { //and pendingPublishCount==0 since we are in the else
	            		//we just finished an ingress message so release it
	                  	PipeReader.releaseReadLock(ingressMessagePipes[pendingReleaseCountIdx]);                   
	            	}
	            }
	        }
	        /////////////////////
	        //we now have the pending work done
	        ////////////////////
	        
	        //ingressMessagePipes
	        int i = ingressMessagePipes.length;
	        while (--i >= 0) {
	        	Pipe<IngressMessages> ingessPipe = ingressMessagePipes[i];  
	
	        	while (PipeReader.tryReadFragment(ingessPipe)) {
	        		foundWork=true;
	                final byte[] backing = PipeReader.readBytesBackingArray(ingessPipe, IngressMessages.MSG_PUBLISH_103_FIELD_TOPIC_1);
	                final int pos = PipeReader.readBytesPosition(ingessPipe, IngressMessages.MSG_PUBLISH_103_FIELD_TOPIC_1);
	                final int len = PipeReader.readBytesLength(ingessPipe, IngressMessages.MSG_PUBLISH_103_FIELD_TOPIC_1);
	                final int mask = PipeReader.readBytesMask(ingessPipe, IngressMessages.MSG_PUBLISH_103_FIELD_TOPIC_1);
	        		int listIdx = subscriptionListIdx(backing, pos, len, mask);
	        		
	        		if (listIdx>=0) {
	        			
	        			if (hasNextSubscriber(listIdx)) {
			                
		        	 		if (enableTrace) {
		        	 			pubSubTrace.init(ingessPipe, IngressMessages.MSG_PUBLISH_103_FIELD_TOPIC_1, 
		        	 										IngressMessages.MSG_PUBLISH_103_FIELD_PAYLOAD_3);
		        	 			logger.info("new message to be routed, {}", pubSubTrace); 
		        	 		}
			        				        	 		
		        	 		int length = subscriberLists[listIdx];
		        	 		final int limit = 1+listIdx+length;
		        	 		
	                    	
	                    	for(int j = listIdx+1; j<limit && hasNextSubscriber(j); j++) {
	                    	
								int pipeIdx = subscriberLists[j];
	                    		
								copyToSubscriber(ingessPipe, pipeIdx,
											IngressMessages.MSG_PUBLISH_103_FIELD_TOPIC_1, 
											IngressMessages.MSG_PUBLISH_103_FIELD_PAYLOAD_3
	                    				);
								
	                    	}
	                	}
	        			
	                    if (pendingPublishCount>0) {
	                    	
	                    	logger.warn("Message PubSub pipes have become full, you may want to consider fewer messages or longer pipes for MessagePubSub outgoing");
	                    	pendingDeliveryType = PubType.Message;                   	
	                        pendingReleaseCountIdx = i; 
	                        
	                        return;//must try again later
	                    } else {
	                    	
	                    	PipeReader.releaseReadLock(ingessPipe);
	                    }
	        			
	        			
	        			
	        		}
	        		
	        	}
	        }
	    	   	
	        
	        
	        ///////////////////
	        //find the next "go" message to be done
	        ///////////////////
	        super.run();
	        
    	} while (foundWork);
    	
    	
    }
    
    @Override
    protected void processMessagesForPipe(int a) {
        
        //TODO: still need to add support for +
        //TODO: still need to add support for #
    	
        
        Pipe<MessagePubSub> pipe = incomingSubsAndPubsPipe[a];
        
             
        long[] targetMakrs = consumedMarks[a];

        
//        logger.info("enter while {}, {}, {} ,{} ,{}",
//        		isPreviousConsumed(a),
//        		PipeReader.hasContentToRead(pipe),
//        		hasReleaseCountRemaining(a),
//        		isChannelUnBlocked(a),
//        		isNotBlockedByStateChange(pipe));
        
        
        while (isPreviousConsumed(a) && //warning this one has side effect and must come first.
        	   PipeReader.hasContentToRead(pipe) && //added for performance reasons so we can quit early	
        	   hasReleaseCountRemaining(a) &&
        	   isChannelUnBlocked(a) &&        	   
        	   isNotBlockedByStateChange(pipe) &&        	   
               PipeReader.tryReadFragment(pipe) 
              ) {
        	foundWork=true;
        	
            int msgIdx = PipeReader.getMsgIdx(pipe);
            //logger.info("consumed message {}",msgIdx);
            
            switch (msgIdx)  {
            	case MessagePubSub.MSG_CHANGESTATE_70:
            		
            		//error because we have not yet put the previous change on all the pipes
            		assert(newState==currentState) : "Attempting to process state change before all listeners have been sent the current state change ";
            		//error because the previous change has not been consumed from all the changes
            		assert(stateChangeInFlight == -1) : "Attempting to process state change before all listeners have consumed the in flight change";
            		            		
            		newState = PipeReader.readInt(pipe, MessagePubSub.MSG_CHANGESTATE_70_FIELD_ORDINAL_7);
            		
            		if (currentState!=newState) {
            			stateChangeInFlight = a;
            			//NOTE: this must go out to all pipes regardless of having state listeners.
            			//      reactors will hold the state to do additional event filtering so all message pipes to require state change messages.
            			pendingAck[a] = true;
            			requiredConsumes[a] = targetMakrs.length; // state changes require all consumers
            			//logger.info("need pending ack for message on {} ",a);
  
            			//SENT TO ALL outgoing pipes regardless...
	                	for(int i = 0; i<outgoingMessagePipes.length; i++) {
	                		copyToSubscriberState(currentState, newState, i, targetMakrs);
	                	}
            		} else {
            			//logger.info("no change so release and clear");
            			PipeReader.releaseReadLock( incomingSubsAndPubsPipe[a]);                
                        decReleaseCount(a);   
            			
            		}
            		
            		//Do nothing else until this is completed.
            		//critical to ensure that ordering is preserved 
            		if (pendingPublishCount>0) {
            			 logger.warn("State change pipes have become full, you may want to consider fewer state changes or longer pipes for MessagePubSub outgoing");
                     	 pendingDeliveryType = PubType.State;
                         pendingReleaseCountIdx = a; //keep so this is only cleared after we have had successful transmit to all subscribers.
                         return;//must try again later
                    } else {
                    	 //done with state changes
                    	 currentState = newState;
                    }
            		break;
                case MessagePubSub.MSG_PUBLISH_103:
                    {                        

                		int ackPolicy = PipeReader.readInt(pipe, MessagePubSub.MSG_PUBLISH_103_FIELD_QOS_5);
                		
                        //find which pipes have subscribed to this topic
                        final byte[] backing = PipeReader.readBytesBackingArray(pipe, MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1);
                        final int pos = PipeReader.readBytesPosition(pipe, MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1);
                        final int len = PipeReader.readBytesLength(pipe, MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1);
                        final int mask = PipeReader.readBytesMask(pipe, MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1);                  
                        
                        //selects the topics pipe
                        int listIdx = subscriptionListIdx(backing, pos, len, mask);
                 
                        if (listIdx>=0) {
                        	if (hasNextSubscriber(listIdx)) {
                        		
                        		if (enableTrace) {        
                        			pubSubTrace.init(pipe, MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1, 
                        				         MessagePubSub.MSG_PUBLISH_103_FIELD_PAYLOAD_3);
                        			//logger.info("new message to be routed, {}", pubSubTrace);
                        		}
	                        		                        	
	                        	//logger.info("need pending ack for message on {} ",a);
	                        	int length = subscriberLists[listIdx];
	                        	final int limit = 1+listIdx+length;
	                        
	                        	pendingAck[a] = true;
	                        	requiredConsumes[a] = WaitFor.computeRequiredCount(ackPolicy, length);
	                        	
	                        	//System.err.println("new publish length "+length+"  "+requiredConsumes[a]);
	                        	
	                        	//only sent to the known subscribers.
	                        	for(int i = listIdx+1; i<=limit && hasNextSubscriber(i); i++) {
	                        		copyToSubscriber(pipe, subscriberLists[i], targetMakrs,
	                        				MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1, 
	                        				MessagePubSub.MSG_PUBLISH_103_FIELD_PAYLOAD_3
	                        				);                                
	                        	}
                        	}
                        	
                        	//Do nothing else until this is completed.
                        	//critical to ensure that ordering is preserved
                            if (pendingPublishCount>0) {
                            	logger.warn("Message PubSub pipes have become full, you may want to consider fewer messages or longer pipes for MessagePubSub outgoing");
                            	pendingDeliveryType = PubType.Message;
                                pendingReleaseCountIdx = a; //keep so this is only cleared after we have had successful transmit to all subscribers.
                                return;//must try again later
                            }                            
                            
                        } else {
                        	logger.info("no subscribers on topic: {} ",Appendables.appendUTF8(new StringBuilder(), backing, pos, len, mask));
                        	PipeReader.releaseReadLock( incomingSubsAndPubsPipe[a]);                
                            decReleaseCount(a);   
                        }

                    }   
                    break;
                case MessagePubSub.MSG_SUBSCRIBE_100:
                    {
                        addSubscription(pipe);
                        PipeReader.releaseReadLock( incomingSubsAndPubsPipe[a]);                
                        decReleaseCount(a); 
                    }              
                    break;
                case MessagePubSub.MSG_UNSUBSCRIBE_101:                    
	                {
	                    unsubscribe(pipe);
	                    PipeReader.releaseReadLock( incomingSubsAndPubsPipe[a]);                
	                    decReleaseCount(a); 
	                } 
	                break;
            }   
        }
    }



	private int subscriptionListIdx(final byte[] backing, final int pos, final int len, final int mask) {
		
		
//		ByteSquenceVisitor visitor = new ByteSquenceVisitor() {
//
//			@Override
//			public void addToResult(long l) {
//				// TODO Auto-generated method stub
//				
//			}
//
//			@Override
//			public void clearResult() {
//				// TODO Auto-generated method stub - remove this??				
//			}
//			
//		};
//		localSubscriptionTrieReader.visit(localSubscriptionTrie, 
//				visitor, 
//				backing, pos, len, mask);
		
		
		int listIdx = (int) TrieParserReader.query(localSubscriptionTrieReader, localSubscriptionTrie, backing, pos, len, mask);
		return listIdx;
	}

    
	private boolean isNotBlockedByStateChange(Pipe<MessagePubSub> pipe) {
		return (stateChangeInFlight == -1) || ( !PipeReader.peekMsg(pipe, MessagePubSub.MSG_CHANGESTATE_70));
	}

	private boolean hasNextSubscriber(int listIdx) {
		return -1 != subscriberLists[listIdx];
	}

	
    private void unsubscribe(short pipeIdx, byte[] backing1, int pos1, int len1, int mask1) {
	
        //////////////////////
		//convert # and + to support escape values
		//////////////////////
		convertMQTTTopicsToLocal(backing1, pos1, len1, mask1);
		
		int msgIdx = Pipe.takeMsgIdx(tempSubject);
		int mta = Pipe.takeRingByteMetaData(tempSubject);
		int len = Pipe.takeRingByteLen(tempSubject);
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
			for(int i = listIdx+1; i<=limit; i++) {
				
			    if (-1 == subscriberLists[i]) {
			        //end of list //subscriberLists[i]=pipeIdx;
			        break;
			    } else if (pipeIdx == subscriberLists[i]){
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

	private void addSubscription(final short pipeIdx, final byte[] backing1, final int pos1, final int len1, final int mask1) {

        //////////////////////
		//convert # and + to support escape values
		//////////////////////
		convertMQTTTopicsToLocal(backing1, pos1, len1, mask1);
		
		int msgIdx = Pipe.takeMsgIdx(tempSubject);
		int mta = Pipe.takeRingByteMetaData(tempSubject);
		int len = Pipe.takeRingByteLen(tempSubject);
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
		
		for(int i = 1+listIdx; i<=limit; i++) {
		    if (-1 == subscriberLists[i]) {
		        subscriberLists[i]=pipeIdx; //outgoing pipe which will be sent this data.
		        subscriberLists[listIdx]++;//incrment the count;
		        break;
		    } else if (pipeIdx == subscriberLists[i]){
		        break;//already in list.
		    }
		}
	}


	private int beginNewSubscriptionList(final byte[] backing1, final int pos1, final int len1, final int mask1,
			int len, int mask, int pos, byte[] backing) {
		int listIdx;
		if (showNewSubscriptions) {
			logger.info("adding new subscription {} as internal pattern {}",
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


	private void convertMQTTTopicsToLocal(final byte[] backing1, final int pos1, final int len1, final int mask1) {
		int size = Pipe.addMsgIdx(tempSubject, RawDataSchema.MSG_CHUNKEDSTREAM_1);
		DataOutputBlobWriter<RawDataSchema> stream = Pipe.outputStream(tempSubject);
		DataOutputBlobWriter.openField(stream);
		TrieParserReader.parseSetup(topicConversionTrieReader, backing1, pos1, len1, mask1);
		boolean foundEnd = false;
		while (TrieParserReader.parseHasContent(topicConversionTrieReader)) {
						
			if (foundEnd) {
				throw new UnsupportedOperationException("Invalid topic if /# is used it must only be at the end.");
			}
			
			int token = (int)TrieParserReader.parseNext(topicConversionTrieReader, topicConversionTrie);
			if (-1 == token) {				
				stream.write(TrieParserReader.parseSkipOne(topicConversionTrieReader));
			} if (1 == token) {
				stream.write(WILD_POUND_THE_END); //  /#
				foundEnd = true;
			} if (2 == token) {
				stream.write(WILD_PLUS_THE_SEGMENT); //  +/
			}
		}
		DataOutputBlobWriter.closeLowLevelField(stream);
		Pipe.confirmLowLevelWrite(tempSubject, size);
		Pipe.publishWrites(tempSubject);
	}
//   
    private void copyToSubscriber(Pipe<?> pipe, int pipeIdx, long[] targetMarks, int topicLOC, int payloadLOC) {
        Pipe<MessageSubscription> outPipe = outgoingMessagePipes[pipeIdx];
        if (PipeWriter.tryWriteFragment(outPipe, MessageSubscription.MSG_PUBLISH_103)) {
        	
            PipeReader.copyBytes(pipe, outPipe, topicLOC, MessageSubscription.MSG_PUBLISH_103_FIELD_TOPIC_1);
            PipeReader.copyBytes(pipe, outPipe, payloadLOC, MessageSubscription.MSG_PUBLISH_103_FIELD_PAYLOAD_3);
            
            //due to batching this may not become the head position upon publish but it will do so eventually.
            //so to track this position we use workingHeadPosition not headPosition
            targetMarks[pipeIdx] = Pipe.workingHeadPosition(outPipe);
            
            PipeWriter.publishWrites(outPipe);
        } else {
        	//add this one back to the list so we can send again later
            pendingPublish[pendingPublishCount++] = pipeIdx;
            pendingIngress = false;
        }
    }
    
    private void copyToSubscriber(Pipe<?> pipe, int pipeIdx, int topicLOC, int payloadLOC) {
        Pipe<MessageSubscription> outPipe = outgoingMessagePipes[pipeIdx];
        if (PipeWriter.tryWriteFragment(outPipe, MessageSubscription.MSG_PUBLISH_103)) {
        	
//        	//debug -- this string is the old value not the new one...
//        	StringBuilder b = new StringBuilder("MessagePubSub:");
//        	PipeReader.readUTF8(pipe, payloadLOC, b);
//        	System.err.println(b);
        	
            PipeReader.copyBytes(pipe, outPipe, topicLOC, MessageSubscription.MSG_PUBLISH_103_FIELD_TOPIC_1);
            PipeReader.copyBytes(pipe, outPipe, payloadLOC, MessageSubscription.MSG_PUBLISH_103_FIELD_PAYLOAD_3);
     
            PipeWriter.publishWrites(outPipe);
        } else {
        	//add this one back to the list so we can send again later
            pendingPublish[pendingPublishCount++] = pipeIdx;    
            pendingIngress = true;
        }
    }

    private void copyToSubscriberState(int oldOrdinal, int newOrdinal, int pipeIdx, long[] targetMarks) {
        Pipe<MessageSubscription> outPipe = outgoingMessagePipes[pipeIdx];
        if (PipeWriter.tryWriteFragment(outPipe, MessageSubscription.MSG_STATECHANGED_71)) {
        	assert(oldOrdinal != newOrdinal) : "Stage change must actualt change the state!";
        	PipeWriter.writeInt(outPipe, MessageSubscription.MSG_STATECHANGED_71_FIELD_OLDORDINAL_8, oldOrdinal);
        	PipeWriter.writeInt(outPipe, MessageSubscription.MSG_STATECHANGED_71_FIELD_NEWORDINAL_9, newOrdinal);
        	            
            //due to batching this may not become the head position upon publish but it will do so eventually.
            //so to track this position we use workingHeadPosition not headPosition
        	targetMarks[pipeIdx] = Pipe.workingHeadPosition(outPipe);
            		
            PipeWriter.publishWrites(outPipe);
        } else {
        	pendingPublish[pendingPublishCount++] = pipeIdx;
        	pendingIngress = false;
        }
    }
    
}
