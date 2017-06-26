package com.ociweb.gl.impl.stage;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.gl.api.HTTPRequestReader;
import com.ociweb.gl.api.HTTPResponseListener;
import com.ociweb.gl.api.ListenerConfig;
import com.ociweb.gl.api.ListenerFilter;
import com.ociweb.gl.api.MessageReader;
import com.ociweb.gl.api.PubSubListener;
import com.ociweb.gl.api.RestListener;
import com.ociweb.gl.api.ShutdownListener;
import com.ociweb.gl.api.StartupListener;
import com.ociweb.gl.api.StateChangeListener;
import com.ociweb.gl.api.TimeListener;
import com.ociweb.gl.impl.BuilderImpl;
import com.ociweb.gl.impl.PayloadReader;
import com.ociweb.gl.impl.schema.MessageSubscription;
import com.ociweb.pronghorn.network.ClientConnection;
import com.ociweb.pronghorn.network.ClientCoordinator;
import com.ociweb.pronghorn.network.config.HTTPContentType;
import com.ociweb.pronghorn.network.config.HTTPSpecification;
import com.ociweb.pronghorn.network.config.HTTPVerbDefaults;
import com.ociweb.pronghorn.network.schema.HTTPRequestSchema;
import com.ociweb.pronghorn.network.schema.NetResponseSchema;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeUTF8MutableCharSquence;
import com.ociweb.pronghorn.stage.PronghornStage;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;

public class ReactiveListenerStage<H extends BuilderImpl> extends PronghornStage implements ListenerFilter {

    protected final Object              listener;
    
    protected final Pipe<?>[]           inputPipes;
    protected final Pipe<?>[]           outputPipes;
    protected int[]                     routeIds;
    protected int[]                     parallelIds;
        
    protected long                      timeTrigger;
    protected long                      timeRate;   
    
    protected H			        		builder;
  
    private static final Logger logger = LoggerFactory.getLogger(ReactiveListenerStage.class); 
     
    protected boolean startupCompleted;
    protected boolean shutdownCompleted;
    
    //all non shutdown listening reactors will be shutdown only after the listeners have finished.
    protected static AtomicInteger liveShutdownListeners = new AtomicInteger();
    protected static AtomicInteger totalLiveReactors = new AtomicInteger();    
    protected static AtomicBoolean shutdownRequsted = new AtomicBoolean(false);
    protected static Runnable lastCall;
    ///////////////////////////
    	
    private boolean restRoutesDefined = false;	
    protected int[] oversampledAnalogValues;

    private static final int MAX_PORTS = 10;
 
    
    protected final Enum[] states;
    
    protected boolean timeEvents = false;
    
    /////////////////////
    //Listener Filters
    /////////////////////  

    private long[] includedToStates;
    private long[] includedFromStates;
    private long[] excludedToStates;
    private long[] excludedFromStates;
		
    /////////////////////
    private Number stageRate;
    protected final GraphManager graphManager;
    protected int timeProcessWindow;

    private PipeUTF8MutableCharSquence workspace = new PipeUTF8MutableCharSquence();
    private PayloadReader payloadReader;
    
    private final StringBuilder workspaceHost = new StringBuilder();
    
    private HTTPSpecification httpSpec;
    
    
    private final ClientCoordinator ccm;

	protected static final long MS_to_NS = 1_000_000;
    private int timeIteration = 0;
    private int parallelInstance;
    //////////////////////////////////////////////////
    ///NOTE: keep all the work here to a minimum, we should just
    //      take data off pipes and hand off to the application
    //      the thread here is the applications thread if
    //      much work needs to be done is must be done elsewhere
    /////////////////////////////////////////////////////

    ///TODO: try to create this actual listener lazy and late so we can filter graph more effectivly
    public ReactiveListenerStage(GraphManager graphManager, Object listener, Pipe<?>[] inputPipes, Pipe<?>[] outputPipes, H builder, int parallelInstance) {
        
        super(graphManager, inputPipes, outputPipes);
        this.listener = listener;
        this.parallelInstance = parallelInstance;
        this.inputPipes = inputPipes;
        this.outputPipes = outputPipes;       
        this.builder = builder;
        
        this.states = builder.getStates();
        this.graphManager = graphManager;
             
        this.ccm = builder.getClientCoordinator();
        
        int totalCount = totalLiveReactors.incrementAndGet();
        assert(totalCount>=0);
        if (listener instanceof ShutdownListener) {
        	int shudownListenrCount = liveShutdownListeners.incrementAndGet();
        	assert(shudownListenrCount>=0);
        }
                
    }
    
    
    public static void requestSystemShutdown(Runnable shutdownRunnable) {
    	lastCall = shutdownRunnable;
    	shutdownRequsted.set(true);
    }

    
    public final void setTimeEventSchedule(long rate, long start) {
        
        timeRate = rate;
        timeTrigger = start;

        timeEvents = (0 != timeRate) && (listener instanceof TimeListener);
    }
    
    @Override
    public void startup() {              
 
    	if (listener instanceof RestListener) {
    		if (!restRoutesDefined) {
    			throw new UnsupportedOperationException("a RestListener requires a call to includeRoutes() first to define which routes it consumes.");
    		}
    	}
    	
    	httpSpec = HTTPSpecification.defaultSpec();
    	 
        stageRate = (Number)GraphManager.getNota(graphManager, this.stageId,  GraphManager.SCHEDULE_RATE, null);
        
        timeProcessWindow = (null==stageRate? 0 : (int)(stageRate.longValue()/MS_to_NS));
        
        
        ///////////////////////
        //build local lookup for the routeIds based on which pipe the data was read from.
        ///////////////////////
        int p = inputPipes.length;
        routeIds = new int[p];
        parallelIds = new int[p];
        
        while (--p >= 0) {
        	 Pipe<?> localPipe = inputPipes[p];
        	 if (Pipe.isForSchema((Pipe<HTTPRequestSchema>)localPipe, HTTPRequestSchema.class)) {
        		 builder.lookupRouteAndPara(localPipe, p, routeIds, parallelIds);
        	 } else {
        		 routeIds[p]=Integer.MIN_VALUE;
        		 parallelIds[p]=Integer.MIN_VALUE;
        	 }
        }     
        
        //Do last so we complete all the initializations first
        if (listener instanceof StartupListener) {
        	((StartupListener)listener).startup();
        }        
        startupCompleted=true;
        
    }

    @Override
    public void run() {
        
    	if (shutdownRequsted.get()) {
    		if (!shutdownCompleted) {
    			
    			if (listener instanceof ShutdownListener) {    				
    				if (((ShutdownListener)listener).acceptShutdown()) {
    					int remaining = liveShutdownListeners.decrementAndGet();
    					assert(remaining>=0);
    					requestShutdown();
    					return;
    				}
    				//else continue with normal run processing
    				
    			} else {
    				//this one is not a listener so we must wait for all the listeners to close first
    				
    				if (0 == liveShutdownListeners.get()) {    					
    					requestShutdown();
    					return;
    				}
    				//else continue with normal run processing.
    				
    			}
    		} else {
    			assert(shutdownCompleted);
    			assert(false) : "run should not have been called if this stage was shut down.";
    			return;
    		}
    	}
    	
    	
        if (timeEvents) {         	
			processTimeEvents((TimeListener)listener, timeTrigger);            
		}
     
        int p = inputPipes.length;
        
        while (--p >= 0) {

        	Pipe<?> localPipe = inputPipes[p];
  
            if (Pipe.isForSchema((Pipe<MessageSubscription>)localPipe, MessageSubscription.class)) {                
            	
            	consumePubSubMessage(listener, (Pipe<MessageSubscription>) localPipe);
            	
            } else if (Pipe.isForSchema((Pipe<NetResponseSchema>)localPipe, NetResponseSchema.class)) {
     
               consumeNetResponse((HTTPResponseListener)listener, (Pipe<NetResponseSchema>) localPipe);
            
            } else if (Pipe.isForSchema((Pipe<HTTPRequestSchema>)localPipe, HTTPRequestSchema.class)) {
            	
            	consumeRestRequest((RestListener)listener, (Pipe<HTTPRequestSchema>) localPipe, routeIds[p], parallelIds[p]);
            
            } else {
                logger.error("unrecognized pipe sent to listener of type {} ", Pipe.schemaName(localPipe));
            }
        }
        
        
    }


	@Override    
    public void shutdown() {
		assert(!shutdownCompleted) : "already shut down why was this called a second time?";
		int i = outputPipes.length;
		while (--i>=0) {
			Pipe<?> output = outputPipes[i];
			Pipe.spinBlockForRoom(output, Pipe.EOF_SIZE);
			Pipe.publishEOF(output);
		}		

		if (totalLiveReactors.decrementAndGet()==0) {
			//ready for full system shutdown.
			if (null!=lastCall) {
				lastCall.run();
			}
		}
		shutdownCompleted = true;
    }

    
    protected final void consumeRestRequest(RestListener listener, Pipe<HTTPRequestSchema> p, final int routeId, final int parallelIdx) {
		
    	  while (Pipe.hasContentToRead(p)) {                
              
    		  Pipe.markTail(p);
             
              int msgIdx = Pipe.takeMsgIdx(p);
    	  
    	      if (HTTPRequestSchema.MSG_RESTREQUEST_300==msgIdx) {
    	    	  
    	    	  //logger.info("consumeRestRequest");
    	    	  
//    	    	    public static final int MSG_RESTREQUEST_300_FIELD_CHANNELID_21 = 0x00800001;
//    	    	    public static final int MSG_RESTREQUEST_300_FIELD_SEQUENCE_26 = 0x00400003;
//    	    	    public static final int MSG_RESTREQUEST_300_FIELD_VERB_23 = 0x00000004;
//    	    	    public static final int MSG_RESTREQUEST_300_FIELD_PARAMS_32 = 0x01c00005;
//    	    	    public static final int MSG_RESTREQUEST_300_FIELD_REVISION_24 = 0x00000007;
//    	    	    public static final int MSG_RESTREQUEST_300_FIELD_REQUESTCONTEXT_25 = 0x00000008; 
    	    	  
    	    	  
    	    	  long connectionId = Pipe.takeLong(p);
    	    	  int sequenceNo = Pipe.takeInt(p);
    	    	  
    	    	  
    	    	  //both these values are required in order to ensure the right sequence order once processed.
    	    	  long sequenceCode = (((long)parallelIdx)<<32) | ((long)sequenceNo);
    	    	  
    	    	  int verbId = Pipe.takeInt(p);
    	    	      	    	  
    	    	  HTTPRequestReader request = (HTTPRequestReader)Pipe.inputStream(p);
    	    	  request.openLowLevelAPIField(); //NOTE: this will take meta then take len
    	        	
    	    	  
    	    	  
    	    	  
    	    	  request.setFieldNameParser(builder.extractionParser(routeId));
    	    	  
 				  request.setHeaderTable(builder.headerToPositionTable(routeId), 
 						                 builder.extractionParserIndexCount(routeId),
 						                 builder.headerTrieParser(routeId)
 						                 );
 				  
    	    	  request.setRevisionId(Pipe.takeInt(p));
    	    	  request.setRequestContext(Pipe.takeInt(p));
    	    	  
    	    	  request.setRouteId(routeId);
    	    	  request.setConnectionId(connectionId, sequenceCode);
    	    	  
    	    	  //assign verbs as strings...
    	    	  request.setVerb((HTTPVerbDefaults)httpSpec.verbs[verbId]);
 			
    	    	  if (!listener.restRequest(request)) {
	            		 Pipe.resetTail(p);
	            		 return;//continue later and repeat this same value.
	              }
             	  
             	 request.setFieldNameParser(null);//just for safety
             	 
    	      } else {
    	    	  logger.error("unrecognized message on {} ",p);
    	    	  throw new UnsupportedOperationException("unexpected message "+msgIdx);
    	      }
              
    	      Pipe.confirmLowLevelRead(p, Pipe.sizeOf(p,msgIdx));
              Pipe.releaseReadLock(p);
              
    	  }
    	
    	
    	
	}


	protected final void consumeNetResponse(HTTPResponseListener listener, Pipe<NetResponseSchema> p) {
				
    	 while (Pipe.hasContentToRead(p)) {                
             
    		 Pipe.markTail(p);
    		 
             int msgIdx = Pipe.takeMsgIdx(p);
             switch (msgIdx) {
	             case NetResponseSchema.MSG_RESPONSE_101:
	            	 
	            	// logger.info("consumeNetResponse");
	            	 
	//            	    public static final int MSG_RESPONSE_101 = 0x00000000;
	//            	    public static final int MSG_RESPONSE_101_FIELD_CONNECTIONID_1 = 0x00800001;
	//            	    public static final int MSG_RESPONSE_101_FIELD_PAYLOAD_3 = 0x01c00003;
	            	 
	            	 long ccId1 = Pipe.takeLong(p);
	            	 ClientConnection cc = (ClientConnection)ccm.get(ccId1);
	            	 
	            	 if (null!=cc) {
		            	 PayloadReader reader = (PayloadReader)Pipe.inputStream(p);
		            	 reader.openLowLevelAPIField();
		            	 
		            	 short statusId = reader.readShort();	
		            	 short typeHeader = reader.readShort();
		            	 short typeId = 0;
		            	 if (6==typeHeader) {//may not have type
		            		 assert(6==typeHeader) : "should be 6 was "+typeHeader;
		            		 typeId = reader.readShort();	            	 
		            		 short headerEnd = reader.readShort();
		            		 assert(-1==headerEnd) : "header end should be -1 was "+headerEnd;
		            	 } else {
		            		 assert(-1==typeHeader) : "header end should be -1 was "+typeHeader;
		            	 }
		           
		            	 
		            	 //Keep   host, port,  statusId, typeId, reader
		            	 
		            	 
		            	 boolean isDone = listener.responseHTTP(cc.getHost(), cc.getPort(), statusId, (HTTPContentType)httpSpec.contentTypes[typeId], reader);            	 
		            	 if (!isDone) {
		            		 Pipe.resetTail(p);
		            		 return;//continue later and repeat this same value.
		            	 }
	            	             	 
	            	 } //else do not send, wait for closed message
	            	 break;
	             case NetResponseSchema.MSG_CLOSED_10:
	            	 
	//            	    public static final int MSG_CLOSED_10 = 0x00000004;
	//            	    public static final int MSG_CLOSED_10_FIELD_HOST_4 = 0x01400001;
	//            	    public static final int MSG_CLOSED_10_FIELD_PORT_5 = 0x00000003;
	            	 
	            	 workspaceHost.setLength(0);
	            	 int meta = Pipe.takeRingByteMetaData(p);
	            	 int len = Pipe.takeRingByteLen(p);
	            	 Pipe.readUTF8(p, workspaceHost, meta, len);
	            	 
	            	 int port = Pipe.takeInt(p);
					
	            	 boolean isDone = listener.responseHTTP(workspaceHost,port,(short)-1,null,null);    
	            	 if (!isDone) {
	            		 Pipe.resetTail(p);
	            		 return;//continue later and repeat this same value.
	            	 }	            	 
	            	 
	            	 break;
	             default:
	                 throw new UnsupportedOperationException("Unknown id: "+msgIdx);
             }
            
             Pipe.confirmLowLevelRead(p, Pipe.sizeOf(p,msgIdx));
             Pipe.releaseReadLock(p);
             
             
    	 }
    			
    	
	}

	
//	public static final int MSG_PUBLISH_103 = 0x00000000;
//	public static final int MSG_PUBLISH_103_FIELD_TOPIC_1 = 0x01400001;
//	public static final int MSG_PUBLISH_103_FIELD_PAYLOAD_3 = 0x01C00003;
//	public static final int MSG_STATECHANGED_71 = 0x00000004;
//	public static final int MSG_STATECHANGED_71_FIELD_OLDORDINAL_8 = 0x00000001;
//	public static final int MSG_STATECHANGED_71_FIELD_NEWORDINAL_9 = 0x00000002;
	
	protected final void consumePubSubMessage(Object listener, Pipe<MessageSubscription> p) {
				
		//TODO: Pipe.markHead(p); change all calls to low level API then add support for mark.		
		
		
		while (Pipe.hasContentToRead(p)) {
			
			Pipe.markTail(p);
			
            int msgIdx = Pipe.takeMsgIdx(p);             		            
            
            switch (msgIdx) {
                case MessageSubscription.MSG_PUBLISH_103:
                    if (listener instanceof PubSubListener) {
                    	                    	
                    	int meta = Pipe.takeRingByteLen(p);
                    	int len = Pipe.takeRingByteMetaData(p);
                    	                   	
                    	
                    	CharSequence topic = workspace.setToField(p, meta, len);
	  
	                    assert(null!=topic) : "Callers must be free to write topic.equals(x) with no fear that topic is null.";
	                    
	                    MessageReader reader = (MessageReader)Pipe.inputStream(p);
	                    reader.openLowLevelAPIField();
	                    
	                    
	                    boolean isDone = ((PubSubListener)listener).message(topic,reader);
		            	if (!isDone) {
		            		 Pipe.resetTail(p);
		            		 return;//continue later and repeat this same value.
		            	}
	                    
                    }
                    break;
                case MessageSubscription.MSG_STATECHANGED_71:
                	if (listener instanceof StateChangeListener) {
                		
                		int oldOrdinal = Pipe.takeInt(p);
                		int newOrdinal = Pipe.takeInt(p); 
                		
                		assert(oldOrdinal != newOrdinal) : "Stage change must actualt change the state!";
                		
                		if (isIncluded(newOrdinal, includedToStates) && isIncluded(oldOrdinal, includedFromStates) &&
                			isNotExcluded(newOrdinal, excludedToStates) && isNotExcluded(oldOrdinal, excludedFromStates) ) {			                			
                			
                			boolean isDone = ((StateChangeListener)listener).stateChange(states[oldOrdinal], states[newOrdinal]);
	   		            	if (!isDone) {
			            		 Pipe.resetTail(p);
			            		 return;//continue later and repeat this same value.
			            	}
                			
                		}
						
                	} else {
                		//Reactive listener can store the state here
                		
                		//TODO: important feature, in the future we can keep the state and add new filters like
                		//      only accept digital reads when we are in state X
                		
                	}
                    break;
                case -1:
                    
                    requestShutdown();
                    Pipe.confirmLowLevelRead(p, Pipe.EOF_SIZE);
                    Pipe.releaseReadLock(p);
                    return;
                   
                default:
                    throw new UnsupportedOperationException("Unknown id: "+msgIdx);
                
            }
            Pipe.confirmLowLevelRead(p, Pipe.sizeOf(p,msgIdx));
            Pipe.releaseReadLock(p);
        }
    }        

	
	protected final void processTimeEvents(TimeListener listener, long trigger) {
		
		long msRemaining = (trigger-builder.currentTimeMillis()); 
		if (msRemaining > timeProcessWindow) {
			//if its not near, leave
			return;
		}
		if (msRemaining>1) {
			try {
				Thread.sleep(msRemaining-1);
			} catch (InterruptedException e) {
			}
		}		
		while (builder.currentTimeMillis() < trigger) {
			Thread.yield();                	
		}
		
		listener.timeEvent(trigger, timeIteration++);
		timeTrigger += timeRate;
	}
   
    
	protected final boolean isNotExcluded(int newOrdinal, long[] excluded) {
    	if (null!=excluded) {
    		return 0 == (excluded[newOrdinal>>6] & (1L<<(newOrdinal & 0x3F)));			
		}
		return true;
	}

    protected final boolean isIncluded(int newOrdinal, long[] included) {
		if (null!=included) {			
			return 0 != (included[newOrdinal>>6] & (1L<<(newOrdinal & 0x3F)));
		}
		return true;
	}
	
	protected final <T> boolean isNotExcluded(T port, T[] excluded) {
		if (null!=excluded) {
			int e = excluded.length;
			while (--e>=0) {
				if (excluded[e]==port) {
					return false;
				}
			}
		}
		return true;
	}

	protected final boolean isNotExcluded(int a, int[] excluded) {
		if (null!=excluded) {
			int e = excluded.length;
			while (--e>=0) {
				if (excluded[e]==a) {
					return false;
				}
			}
		}
		return true;
	}
	
	protected final <T> boolean isIncluded(T port, T[] included) {
		if (null!=included) {
			int i = included.length;
			while (--i>=0) {
				if (included[i]==port) {
					return true;
				}
			}
			return false;
		}
		return true;
	}
	
	protected final boolean isIncluded(int a, int[] included) {
		if (null!=included) {
			int i = included.length;
			while (--i>=0) {
				if (included[i]==a) {
					return true;
				}
			}
			return false;
		}
		return true;
	}

	@Override
	public final ListenerFilter includeRoutes(int... routeIds) {

		if (listener instanceof RestListener) {
			int count = 0;
			int i =	inputPipes.length;
			while (--i>=0) {
				//we only expect to find a single request pipe
				if (Pipe.isForSchema(inputPipes[i], HTTPRequestSchema.class)) {		
				   //NOTE: count may be backwards !!if so reverse while on 595?
					int x = routeIds.length;
					int p = parallelInstance==-1?count:parallelInstance;
					while (--x>=0) {
						restRoutesDefined = true;
						builder.appendPipeMapping((Pipe<HTTPRequestSchema>) inputPipes[i], routeIds[x], p);
					}
					count++;
				}
			}
			return this;
		} else {
			throw new UnsupportedOperationException("The Listener must be an instance of "+RestListener.class.getSimpleName()+" in order to call this method.");
		}
	}

	
	@Override
	public final ListenerFilter addSubscription(CharSequence topic) {		
		if (!startupCompleted && listener instanceof PubSubListener) {
			builder.addStartupSubscription(topic, System.identityHashCode(listener));		
			return this;
		} else {
			if (startupCompleted) {
	    		throw new UnsupportedOperationException("Call addSubscription on CommandChanel to modify subscriptions at runtime.");
	    	} else {
	    		throw new UnsupportedOperationException("The Listener must be an instance of "+PubSubListener.class.getSimpleName()+" in order to call this method.");
	    	}
		}
	}

	@Override
	public final <E extends Enum<E>> ListenerFilter includeStateChangeTo(E ... state) {	
		if (!startupCompleted && listener instanceof StateChangeListener) {
			includedToStates = buildMaskArray(state);
			return this;
		} else {
			if (startupCompleted) {
	    		throw new UnsupportedOperationException("ListenerFilters may only be set before startup is called.  Eg. the filters can not be changed at runtime.");
	    	} else {
	    		throw new UnsupportedOperationException("The Listener must be an instance of "+StateChangeListener.class.getSimpleName()+" in order to call this method.");
	    	}
		}
	}

	@Override
	public final <E extends Enum<E>> ListenerFilter excludeStateChangeTo(E ... state) {
		if (!startupCompleted && listener instanceof StateChangeListener) {
			excludedToStates = buildMaskArray(state);
			return this;
		} else {
			if (startupCompleted) {
	    		throw new UnsupportedOperationException("ListenerFilters may only be set before startup is called.  Eg. the filters can not be changed at runtime.");
	    	} else {
	    		throw new UnsupportedOperationException("The Listener must be an instance of "+StateChangeListener.class.getSimpleName()+" in order to call this method.");
	    	}
		}
	}

	
	@Override
	public final <E extends Enum<E>> ListenerFilter includeStateChangeToAndFrom(E ... state) {
		return includeStateChangeTo(state).includeStateChangeFrom(state);
	}
	
	@Override
	public final <E extends Enum<E>> ListenerFilter includeStateChangeFrom(E ... state) {
		if (!startupCompleted && listener instanceof StateChangeListener) {
			includedFromStates = buildMaskArray(state);
			return this;
		} else {
			if (startupCompleted) {
	    		throw new UnsupportedOperationException("ListenerFilters may only be set before startup is called.  Eg. the filters can not be changed at runtime.");
	    	} else {
	    		throw new UnsupportedOperationException("The Listener must be an instance of "+StateChangeListener.class.getSimpleName()+" in order to call this method.");
	    	}
		}
	}

	@Override
	public final <E extends Enum<E>> ListenerFilter excludeStateChangeFrom(E ... state) {
		if (!startupCompleted && listener instanceof StateChangeListener) {
			excludedFromStates = buildMaskArray(state);
			return this;
		} else {
			if (startupCompleted) {
	    		throw new UnsupportedOperationException("ListenerFilters may only be set before startup is called.  Eg. the filters can not be changed at runtime.");
	    	} else {
	    		throw new UnsupportedOperationException("The Listener must be an instance of "+StateChangeListener.class.getSimpleName()+" in order to call this method.");
	    	}
		}
	} 
	
	private final <E extends Enum<E>> long[] buildMaskArray(E[] state) {
		int maxOrdinal = findMaxOrdinal(state);
		int a = maxOrdinal >> 6;
		int b = maxOrdinal & 0x3F;		
		int longsCount = a+(b==0?0:1);
		
		long[] array = new long[longsCount+1];
				
		int i = state.length;
		while (--i>=0) {			
			int ordinal = state[i].ordinal();			
			array[ordinal>>6] |=  1L << (ordinal & 0x3F);			
		}
		return array;
	}

	private final <E extends Enum<E>> int findMaxOrdinal(E[] state) {
		int maxOrdinal = -1;
		int i = state.length;
		while (--i>=0) {
			maxOrdinal = Math.max(maxOrdinal, state[i].ordinal());
		}
		return maxOrdinal;
	}


    
    
}
