package com.ociweb.gl.impl.stage;

import com.ociweb.gl.api.MsgRuntime;
import com.ociweb.gl.impl.BuilderImpl;
import com.ociweb.gl.impl.schema.*;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeReader;
import com.ociweb.pronghorn.pipe.util.hash.IntHashTable;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;
import com.ociweb.pronghorn.util.Appendables;

import static com.ociweb.pronghorn.pipe.Pipe.*;

import java.util.Arrays;

public class MessagePubSubTrafficStage extends AbstractTrafficOrderedStage {


	MessagePubSubImpl data = new MessagePubSubImpl(false, -1, new MessagePubSubTrace());

	
	/**
     * Provides an eventually consistent state model of events.  It works in the same way as the larger universe.  If a supernova is observed by two planets they may
     * not know about it at the same moment but the first who observes it can not send a message to the second that would arrive before the second observes the
     * event themselves.
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
    
    public MessagePubSubTrafficStage(GraphManager gm,
    						  MsgRuntime<?,?,?> runtime,
    						  IntHashTable subscriptionPipeLookup, BuilderImpl hardware, 
    						  Pipe<IngressMessages>[] ingressMessagePipes,
    						  
    		                  Pipe<MessagePubSub>[] incomingSubsAndPubsPipe,
                              Pipe<TrafficReleaseSchema>[] goPipe, //NOTE: can be null for no cop
                              Pipe<TrafficAckSchema>[] ackPipe,  //NOTE: can be null for no cop
                              
                              Pipe<MessageSubscription>[] outgoingMessagePipes) {
       super(gm, runtime, hardware, join(ingressMessagePipes, incomingSubsAndPubsPipe), goPipe, ackPipe, outgoingMessagePipes);

       data.construct(this, gm, subscriptionPipeLookup, hardware, ingressMessagePipes, incomingSubsAndPubsPipe, goPipe, ackPipe,
			outgoingMessagePipes);
	   
    }


	@Override
    public void startup() {
        super.startup();
        data.startupPubSub(this.hardware);      
 
    }


	@Override
    public void run() {

    	do {
    		data.foundWork = false;
	    	//////////////////////
	    	//process the pending publications, this must be completed before we continue
	    	//////////////////////
	        if (data.pendingPublishCount>0) { //must do these first.
	        	data.processPending();
	            if (data.pendingPublishCount>0) {
	            	//do not pick up new work until this is done or we may get out of order messages.
	                return;//try again later
	            } else {
	            	if (data.pendingIngress) { //and pendingPublishCount==0 since we are in the else
	            		//we just finished an ingress message so release it
	                  	PipeReader.releaseReadLock(data.ingressMessagePipes[data.pendingReleaseCountIdx]);                   
	            	}
	            }
	        }
	        /////////////////////
	        //we now have the pending work done
	        ////////////////////
	        
	        //ingressMessagePipes
	        int i = data.ingressMessagePipes.length;
	        while (--i >= 0) {
	        	Pipe<IngressMessages> ingessPipe = data.ingressMessagePipes[i];  
	
	        	while (PipeReader.tryReadFragment(ingessPipe)) {
	        		int listIdx = data.ingressCollectSubLists(ingessPipe);
            		            		
	        		if (listIdx>=0) {
	        			
	        			data.readIngress(ingessPipe, listIdx);
	        			
	                    if (data.pendingPublishCount>0) {
	                    	
	                    	MessagePubSubImpl.logger.warn("Message PubSub pipes have become full, you may want to consider fewer messages or longer pipes for MessagePubSub outgoing in graph {} [1]",data.graphName);
	                    	data.pendingDeliveryType = MessagePubSubImpl.PubType.Message;                   	
	                        data.pendingReleaseCountIdx = i; 
	                        
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
	        
    	} while (data.foundWork);
    	
    	
    }


	@Override
    protected void processMessagesForPipe(int a) {

        Pipe<MessagePubSub> pipe = data.incomingSubsAndPubsPipe[a];      
             
        long[] targetMakrs = data.consumedMarks[a];

        
//        logger.info("enter while {}, {}, {} ,{} ,{}, {}, {}",
//        		isPreviousConsumed(a), //true
//        		PipeReader.hasContentToRead(pipe),
//        		hasReleaseCountRemaining(a),
//        		isChannelUnBlocked(a), //true
//        		isNotBlockedByStateChange(pipe), //true
//        		a, pipe);
        
        
        while (isPreviousConsumed(data, a) && //warning this one has side effect and must come first.
        	   PipeReader.hasContentToRead(pipe) && //added for performance reasons so we can quit early	
        	   hasReleaseCountRemaining(a) &&
        	   isChannelUnBlocked(a) &&        	   
        	   data.isNotBlockedByStateChange(pipe) &&        	   
               PipeReader.tryReadFragment(pipe) 
              ) {
        	data.foundWork=true;

            int msgIdx = PipeReader.getMsgIdx(pipe);
                        
            switch (msgIdx)  {
            	case MessagePubSub.MSG_CHANGESTATE_70:
            		
            		//error because the previous change has not been consumed from all the changes
            		assert(data.stateChangeInFlight == -1) : "Attempting to process state change before all listeners have consumed the in flight change";

            		data.newState = PipeReader.readInt(pipe, MessagePubSub.MSG_CHANGESTATE_70_FIELD_ORDINAL_7);
            		
            		if (data.currentState!=data.newState) {
            			data.stateChangeInFlight = a;
            			//NOTE: this must go out to all pipes regardless of having state listeners.
            			//      reactors will hold the state to do additional event filtering so all message pipes to require state change messages.
            			data.pendingAck[a] = true;
            			data.requiredConsumes[a] = targetMakrs.length; // state changes require all consumers
            			//logger.info("need pending ack for message on {} ",a);
  
            			//SENT TO ALL outgoing pipes regardless...
	                	for(int i = 0; i<data.outgoingMessagePipes.length; i++) {
	                		data.copyToSubscriberState(data.currentState, data.newState, i, targetMakrs);
	                	}
            		} else {
            			//logger.info("no change so release and clear");
            			PipeReader.releaseReadLock( data.incomingSubsAndPubsPipe[a]);                
                        decReleaseCount(a);   
            			
            		}
            		
            		//Do nothing else until this is completed.
            		//critical to ensure that ordering is preserved 
            		if (data.pendingPublishCount>0) {
            			 MessagePubSubImpl.logger.warn("State change pipes have become full, you may want to consider fewer state changes or longer pipes for MessagePubSub outgoing");
                     	 data.pendingDeliveryType = MessagePubSubImpl.PubType.State;
                         data.pendingReleaseCountIdx = a; //keep so this is only cleared after we have had successful transmit to all subscribers.
                         return;//must try again later
                    } else {
                    	 //done with state changes
                    	 data.currentState = data.newState;
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
                        int listIdx = data.subscriptionListIdx(backing, pos, len, mask);
                        				

                		data.collectAllSubscriptionLists(backing, pos, len, mask);
                		IntHashTable.clear(data.deDupeTable); //clear so we can find duplicates
                		
                        
                        if (listIdx>=0) {
                        	data.readNormalMessages(a, pipe, targetMakrs, ackPolicy, listIdx);
                        	
                        	//Do nothing else until this is completed.
                        	//critical to ensure that ordering is preserved
                            if (data.pendingPublishCount>0) {
                            	MessagePubSubImpl.logger.warn("Message PubSub pipes have become full, you may want to consider fewer messages or longer pipes for MessagePubSub outgoing in graph {} [2]",data.graphName);
                            	data.pendingDeliveryType = MessagePubSubImpl.PubType.Message;
                                data.pendingReleaseCountIdx = a; //keep so this is only cleared after we have had successful transmit to all subscribers.
                                return;//must try again later
                            }                            
                            
                        } else {
                        	MessagePubSubImpl.logger.info("no subscribers on topic: {} ",Appendables.appendUTF8(new StringBuilder(), backing, pos, len, mask));
                        	PipeReader.releaseReadLock( data.incomingSubsAndPubsPipe[a]);                
                            decReleaseCount(a);   
                        }

                    }   
                    break;
                case MessagePubSub.MSG_SUBSCRIBE_100:
                    {
                        data.addSubscription(pipe);
                        PipeReader.releaseReadLock( data.incomingSubsAndPubsPipe[a]);                
                        decReleaseCount(a); 
                    }              
                    break;
                case MessagePubSub.MSG_UNSUBSCRIBE_101:                    
	                {
	                    data.unsubscribe(pipe);
	                    PipeReader.releaseReadLock( data.incomingSubsAndPubsPipe[a]);                
	                    decReleaseCount(a); 
	                } 
	                break;
            }   
        }
        
        
        
        
    }


	boolean isPreviousConsumed(MessagePubSubImpl messagePubSubImpl, int incomingPipeId) {
	    	
		long[] marks = messagePubSubImpl.consumedMarks[incomingPipeId];
		int totalUnconsumed = messagePubSubImpl.countUnconsumed(marks, 0);
	
		int totalConsumers = marks.length;
		if ( (totalConsumers-totalUnconsumed) < messagePubSubImpl.requiredConsumes[incomingPipeId]  ) {
			return false;
		}
		
		if (totalUnconsumed>0) {
			Arrays.fill(marks, 0);
		}
		//if this is waiting for an ack send it and clear the value
		if (messagePubSubImpl.pendingAck[incomingPipeId]) {   
			PipeReader.releaseReadLock( messagePubSubImpl.incomingSubsAndPubsPipe[incomingPipeId]); 
		    		
		    decReleaseCount(incomingPipeId);  		
			
		    messagePubSubImpl.pendingAck[incomingPipeId] = false;
			
			//if this ack was for the state change in flight clear it
			if (messagePubSubImpl.stateChangeInFlight == incomingPipeId) {
				messagePubSubImpl.stateChangeInFlight = -1;
			}
		}
		
		return true;//consumer has moved tail past all marks
		
	}
    
}
