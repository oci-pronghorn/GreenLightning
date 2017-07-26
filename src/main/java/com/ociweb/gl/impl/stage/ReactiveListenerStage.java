package com.ociweb.gl.impl.stage;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.gl.api.Behavior;
import com.ociweb.gl.api.HTTPRequestReader;
import com.ociweb.gl.api.HTTPResponseListener;
import com.ociweb.gl.api.HTTPResponseReader;
import com.ociweb.gl.api.ListenerFilter;
import com.ociweb.gl.api.MsgCommandChannel;
import com.ociweb.gl.api.PubSubListener;
import com.ociweb.gl.api.RestListener;
import com.ociweb.gl.api.ShutdownListener;
import com.ociweb.gl.api.StartupListener;
import com.ociweb.gl.api.StateChangeListener;
import com.ociweb.gl.api.TimeListener;
import com.ociweb.gl.impl.BuilderImpl;
import com.ociweb.gl.impl.ChildClassScanner;
import com.ociweb.gl.impl.HTTPResponseListenerBase;
import com.ociweb.gl.impl.PayloadReader;
import com.ociweb.gl.impl.PubSubListenerBase;
import com.ociweb.gl.impl.RestListenerBase;
import com.ociweb.gl.impl.schema.MessageSubscription;
import com.ociweb.gl.impl.schema.TrafficOrderSchema;
import com.ociweb.pronghorn.network.ClientCoordinator;
import com.ociweb.pronghorn.network.config.HTTPContentType;
import com.ociweb.pronghorn.network.config.HTTPHeaderDefaults;
import com.ociweb.pronghorn.network.config.HTTPRevision;
import com.ociweb.pronghorn.network.config.HTTPSpecification;
import com.ociweb.pronghorn.network.config.HTTPVerb;
import com.ociweb.pronghorn.network.config.HTTPVerbDefaults;
import com.ociweb.pronghorn.network.schema.HTTPRequestSchema;
import com.ociweb.pronghorn.network.schema.NetResponseSchema;
import com.ociweb.pronghorn.pipe.BlobReader;
import com.ociweb.pronghorn.pipe.DataInputBlobReader;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeUTF8MutableCharSquence;
import com.ociweb.pronghorn.pipe.util.hash.IntHashTable;
import com.ociweb.pronghorn.stage.PronghornStage;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;
import com.ociweb.pronghorn.util.TrieParser;
import com.ociweb.pronghorn.util.TrieParserReader;

public class ReactiveListenerStage<H extends BuilderImpl> extends PronghornStage implements ListenerFilter {

    private static final int SIZE_OF_MSG_STATECHANGE = Pipe.sizeOf(MessageSubscription.instance, MessageSubscription.MSG_STATECHANGED_71);
	private static final int SIZE_OF_MSG_PUBLISH = Pipe.sizeOf(MessageSubscription.instance, MessageSubscription.MSG_PUBLISH_103);
	protected final Object              listener;
    protected final TimeListener[]      timeListeners;
    
    protected final Pipe<?>[]           inputPipes;
    protected final Pipe<?>[]           outputPipes;
        
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
    

	///////////////////
	//only used for direct method dispatch upon subscription topic arrival
	//////////////////
	private TrieParser methodLookup;
	private TrieParserReader methodReader;
	private CallableStaticMethod[] methods;
	//////////////////
	
	    	
    private boolean restRoutesDefined = false;	
    protected int[] oversampledAnalogValues;

    private static final int MAX_PORTS = 10;
 
    
    public static final ReactiveOperators operators = reactiveOperators();
	 
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

    private PipeUTF8MutableCharSquence mutableTopic = new PipeUTF8MutableCharSquence();
    private PayloadReader payloadReader;
    
    private HTTPSpecification httpSpec;
    private IntHashTable headerToPositionTable; //for HTTPClient
    private TrieParser headerTrieParser; //for HTTPClient
   
    
    private final ClientCoordinator ccm;
    protected ReactiveManagerPipeConsumer consumer;

	protected static final long MS_to_NS = 1_000_000;
    private int timeIteration = 0;
    private int parallelInstance;
    
    private final ArrayList<ReactiveManagerPipeConsumer> consumers;
    
    //////////////////////////////////////////////////
    ///NOTE: keep all the work here to a minimum, we should just
    //      take data off pipes and hand off to the application
    //      the thread here is the applications thread if
    //      much work needs to be done is must be done elsewhere
    /////////////////////////////////////////////////////

    public ReactiveListenerStage(GraphManager graphManager, Behavior listener, 
    		                     Pipe<?>[] inputPipes, Pipe<?>[] outputPipes, 
    		                     ArrayList<ReactiveManagerPipeConsumer> consumers, 
    		                     H builder, int parallelInstance) {
        
        super(graphManager, consumerJoin(inputPipes, consumers.iterator()), outputPipes);
        this.listener = listener;
        assert(null!=listener) : "Behavior must be defined";
        this.parallelInstance = parallelInstance;
        this.consumers = consumers;
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
        
        //TODO: add child object here as well?
        if (listener instanceof TimeListener) {
        	timeListeners = new TimeListener[]{(TimeListener)listener};
        } else {
        	timeListeners = new TimeListener[0];
        }
                
    }

    private static Pipe[] consumerJoin(Pipe<?>[] inputPipes,
    		                   Iterator<ReactiveManagerPipeConsumer> iterator) {
    	if (iterator.hasNext()) {
    		
    		return consumerJoin(join(inputPipes, iterator.next().inputs),iterator);    		
    	} else {
    		return inputPipes;
    	}
    }

    
    private static ReactiveOperators reactiveOperators() {
		return new ReactiveOperators()
        		                 .addOperator(PubSubListenerBase.class, 
        		                		 MessageSubscription.instance,
        		                		 new ReactiveOperator() {
									@Override
									public void apply(Object target, Pipe input, ReactiveListenerStage r) {
										r.consumePubSubMessage(target, input);										
									}        		                	 
        		                 })
        		                 .addOperator(HTTPResponseListenerBase.class, 
        		                		 NetResponseSchema.instance,
        		                		 new ReactiveOperator() {
 									@Override
 									public void apply(Object target, Pipe input, ReactiveListenerStage r) {
 										r.consumeNetResponse((HTTPResponseListener)target, input);										
 									}        		                	 
         		                 })
        		                 .addOperator(RestListenerBase.class, 
        		                		 HTTPRequestSchema.instance,
        		                		 new ReactiveOperator() {
 									@Override
 									public void apply(Object target, Pipe input, ReactiveListenerStage r) {
 										r.consumeRestRequest((RestListener)target, input);										
 									}        		                	 
         		                 });
	}
    
    public int getId() {
    	return builder.behaviorId((Behavior)listener);
    }
    
    public static boolean isShutdownRequested() {
    	return shutdownRequsted.get();
    }
    
    public static void requestSystemShutdown(Runnable shutdownRunnable) {
    	lastCall = shutdownRunnable;
    	shutdownRequsted.set(true);
    	//logger.info("shutdown requested");
    }


	private String toStringDetails = "\n";
    public String toString() {
    
    	return (null==listener ? "Unknown Behavior" :
    		    listener.getClass().getSimpleName())+"\n"+
    	       super.toString()+toStringDetails;    	
    }
    
    public final void setTimeEventSchedule(long rate, long start) {
        
        timeRate = rate;
        timeTrigger = start;

        timeEvents = (0 != timeRate) && (listener instanceof TimeListener);
    }
    
    @Override
    public void startup() {              
 
    	//////////////////////////////////////////////////////////////////
    	//ALL operators have been added to operators so it can be used to create consumers as needed
    	consumer = new ReactiveManagerPipeConsumer(listener, operators, inputPipes);
    	    	
    	if (listener instanceof RestListener) {
    		if (!restRoutesDefined) {
    			throw new UnsupportedOperationException("a RestListener requires a call to includeRoutes() first to define which routes it consumes.");
    		}
    	}
    	
    	httpSpec = HTTPSpecification.defaultSpec();   	 
    	
    	//////////////////
    	///HTTPClient support
    	TrieParserReader parserReader = new TrieParserReader(2, true);
	    headerToPositionTable = httpSpec.headerTable(parserReader);
	    headerTrieParser = httpSpec.headerParser();
    	//////////////////
	    //////////////////
	    
	    
        stageRate = (Number)GraphManager.getNota(graphManager, this.stageId,  GraphManager.SCHEDULE_RATE, null);
        
        timeProcessWindow = (null==stageRate? 0 : (int)(stageRate.longValue()/MS_to_NS));
         
        //TODO: the transducers need to be listed here as startup listeners.
        
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
			processTimeEvents(timeListeners, timeTrigger);            
		}
        
        //behaviors
        consumer.process(this);
        
        //all transducers
        int j = consumers.size();
        while(--j>=0) {
        	consumers.get(j).process(this);
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
				new Thread(lastCall).start();
			}
		}
		shutdownCompleted = true;
    }

    
    final void consumeRestRequest(RestListener listener, Pipe<HTTPRequestSchema> p) {
		
    	  while (Pipe.hasContentToRead(p)) {                
              
    		  Pipe.markTail(p);             
              int msgIdx = Pipe.takeMsgIdx(p);
    	  
    	      if (HTTPRequestSchema.MSG_RESTREQUEST_300==msgIdx) {
    	    	 
    	    	  long connectionId = Pipe.takeLong(p);
    	    	  int sequenceNo = Pipe.takeInt(p);    	    	  

    	    	  int routeVerb = Pipe.takeInt(p);
    	    	  int routeId = routeVerb>>>HTTPVerb.BITS;
    	    	  int verbId = HTTPVerb.MASK & routeVerb;
    	    	  
    	    	      	    	  
    	    	  HTTPRequestReader reader = (HTTPRequestReader)Pipe.inputStream(p);
    	    	  reader.openLowLevelAPIField(); //NOTE: this will take meta then take len
    	        	    	    	  
 				  reader.setParseDetails( builder.routeExtractionParser(routeId),
 						                  builder.routeHeaderToPositionTable(routeId), 
 						                  builder.routeExtractionParserIndexCount(routeId),
 						                  builder.routeHeaderTrieParser(routeId)
 						                 );
 				  
    	    	  int parallelRevision = Pipe.takeInt(p);
    	    	  int parallelIdx = parallelRevision >>> HTTPRevision.BITS;
    	    	  int revision = HTTPRevision.MASK & parallelRevision;
    	    	  
				  reader.setRevisionId(revision);
    	    	  reader.setRequestContext(Pipe.takeInt(p));  
    	    
    	    	  reader.setRouteId(routeId);
    	    	  
    	    	  //both these values are required in order to ensure the right sequence order once processed.
    	    	  long sequenceCode = (((long)parallelIdx)<<32) | ((long)sequenceNo);
    	    	  
    	    	  reader.setConnectionId(connectionId, sequenceCode);
    	    	  
    	    	  //assign verbs as strings...
    	    	  reader.setVerb((HTTPVerbDefaults)httpSpec.verbs[verbId]);
 			
    	    	  if (!listener.restRequest(reader)) {
	            		 Pipe.resetTail(p);
	            		 return;//continue later and repeat this same value.
	              }
             	      
    	    	  reader.setParseDetails(null,null,0,null);//just to be safe.
    	      
    	      } else {
    	    	  logger.error("unrecognized message on {} ",p);
    	    	  throw new UnsupportedOperationException("unexpected message "+msgIdx);
    	      }
        
    	      Pipe.confirmLowLevelRead(p, Pipe.sizeOf(p,msgIdx));
              Pipe.releaseReadLock(p);
              
    	  }
    	   	
    	
	}

    private final HeaderTypeCapture htc  = new HeaderTypeCapture();
    
    
	final void consumeNetResponse(HTTPResponseListener listener, Pipe<NetResponseSchema> p) {
		 assert(null!=ccm) : "must define coordinator";
		 
    	 while (Pipe.hasContentToRead(p)) {                
             
       		 Pipe.markTail(p);
    		 
             int msgIdx = Pipe.takeMsgIdx(p);
             
             //logger.info("response from HTTP request. Type is {} ",msgIdx);
             
             switch (msgIdx) {
	             case NetResponseSchema.MSG_RESPONSE_101:

	            	 long ccId1 = Pipe.takeLong(p);
	            	 //ClientConnection cc = (ClientConnection)ccm.get(ccId1);
	            	 
            		 HTTPResponseReader reader = (HTTPResponseReader)Pipe.inputStream(p);
	            	 reader.openLowLevelAPIField();
	
	            	 final short statusId = reader.readShort();	
				     reader.setParseDetails(headerToPositionTable, headerTrieParser);
				
	            	 reader.openHeaderData(HTTPHeaderDefaults.CONTENT_TYPE.rootBytes(), htc);
	            	 	            	 
	            	 if (!listener.responseHTTP( statusId, 
		            			                 (HTTPContentType)httpSpec.contentTypes[htc.type()],
		            			                 reader)) {
	            		 Pipe.resetTail(p);
	            		 return;//continue later and repeat this same value.
	            	 }
	                 
	            	 //TODO: application layer can not know that the response is complete or we will have a continuation...
	            	 //      they will have to read the headers to know?
	            	 
	            	 break;
	             case NetResponseSchema.MSG_CONTINUATION_102:
	            	 long fieldConnectionId = Pipe.takeLong(p);
            		 HTTPResponseReader continuation = (HTTPResponseReader)Pipe.inputStream(p);
            		 continuation.openLowLevelAPIField();
	            	 
	            	 if (!listener.responseHTTP((short)0,(HTTPContentType)null,continuation)) {
						 Pipe.resetTail(p);
						 return;//continue later and repeat this same value.
					 }
            		 
	            	 break;
	             case NetResponseSchema.MSG_CLOSED_10:

	            	 HTTPResponseReader hostReader = (HTTPResponseReader)Pipe.inputStream(p);
	            	 hostReader.openLowLevelAPIField();
	            	 
	            	 int port = Pipe.takeInt(p);//the caller does not care which port we were on.
					   
	            	 if (!listener.responseHTTP((short)-1,null,hostReader)) {
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

	
	final void consumePubSubMessage(Object listener, Pipe<MessageSubscription> p) {

		while (Pipe.hasContentToRead(p)) {
			
			Pipe.markTail(p);
			
            final int msgIdx = Pipe.takeMsgIdx(p);             		            
            
            switch (msgIdx) {
                case MessageSubscription.MSG_PUBLISH_103:
                    	
                    	final int meta = Pipe.takeRingByteLen(p);
                    	final int len = Pipe.takeRingByteMetaData(p);                    	
                    	final int pos = Pipe.convertToPosition(meta, p);
                    	                    	               	
                    	mutableTopic.setToField(p, meta, len);
	  
	                    DataInputBlobReader<MessageSubscription> reader = Pipe.inputStream(p);
	                    reader.openLowLevelAPIField();
	                    	                           
	                    int dispatch;
	                    
	                    if ((null==methodReader) || ((dispatch=methodLookup(p, len, pos))<0)) {
	                    	if (! ((PubSubListener)listener).message(mutableTopic,reader)) {
	                    		Pipe.resetTail(p);
			            		return;//continue later and repeat this same value.
	                    	}
	                    } else {
	                    	if (! methods[dispatch].method(listener, mutableTopic, reader)) {
	                    		Pipe.resetTail(p);
	                    		return;//continue later and repeat this same value.	                    		
	                    	}
	                    }
 
                        Pipe.confirmLowLevelRead(p, SIZE_OF_MSG_PUBLISH);
                    break;
                case MessageSubscription.MSG_STATECHANGED_71:

                		int oldOrdinal = Pipe.takeInt(p);
                		int newOrdinal = Pipe.takeInt(p); 
                		
                		assert(oldOrdinal != newOrdinal) : "Stage change must actualt change the state!";
                		
                		if (isIncluded(newOrdinal, includedToStates) && isIncluded(oldOrdinal, includedFromStates) &&
                			isNotExcluded(newOrdinal, excludedToStates) && isNotExcluded(oldOrdinal, excludedFromStates) ) {			                			
                			
                			if (!((StateChangeListener)listener).stateChange(states[oldOrdinal], states[newOrdinal])) {
			            		 Pipe.resetTail(p);
			            		 return;//continue later and repeat this same value.
			            	}
                			
                		}
			
                        Pipe.confirmLowLevelRead(p, SIZE_OF_MSG_STATECHANGE);
                    break;
                case -1:
                    
                    requestShutdown();
                    Pipe.confirmLowLevelRead(p, Pipe.EOF_SIZE);
                    Pipe.releaseReadLock(p);
                    return;
                   
                default:
                    throw new UnsupportedOperationException("Unknown id: "+msgIdx);
                
            }
            Pipe.releaseReadLock(p);
        }
    }

	private final int methodLookup(Pipe<MessageSubscription> p, final int len, final int pos) {
		return (int)TrieParserReader.query(methodReader, methodLookup,
				Pipe.blob(p), pos, len, Pipe.blobMask(p));
	}        

	
	protected final void processTimeEvents(TimeListener[] listener, long trigger) {
		
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
		int iteration = timeIteration++;
		
		//all Internal Objects will get these sequentially 
		for(int i = 0; i<listener.length; i++) {
			listener[i].timeEvent(trigger, iteration);
		}		
		
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
	public final ListenerFilter includeAllRoutes() {
		
		restRoutesDefined = true;
		
		if (listener instanceof RestListener) {
			int count = 0;
			int i =	inputPipes.length;
			while (--i>=0) {
				//we only expect to find a single request pipe
				if (Pipe.isForSchema(inputPipes[i], HTTPRequestSchema.class)) {		
				   
					int routes = builder.routerConfig().routesCount();
					int p = parallelInstance==-1?count:parallelInstance;
					
					assert(routes>=0);
					///////////
					//for catch all
					///////////
					if (routes==0) {
						routes=1;
					}
					
					while (--routes>=0) {
						builder.appendPipeMapping((Pipe<HTTPRequestSchema>) inputPipes[i], routes, p);
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
	public final ListenerFilter includeRoutes(int... routeIds) {

		if (listener instanceof RestListener) {
			int count = 0;
			int i =	inputPipes.length;
			while (--i>=0) {
				//we only expect to find a single request pipe
				if (Pipe.isForSchema(inputPipes[i], HTTPRequestSchema.class)) {		
				  
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

	@SuppressWarnings("unchecked")
	public final ListenerFilter addSubscription(
			CharSequence topic, 
			final CallableMethod callable) {
		
		return addSubscription(topic, new CallableStaticMethod() {
			@Override
			public boolean method(Object that, CharSequence title, BlobReader reader) {
				//that must be found as the declared field of the lambda
				assert(thatIsFoundInCallable(that,callable)) : "may only call methods on this same Behavior instance";
				return callable.method(title, reader);
			}
		});
		
	}
	
	private boolean thatIsFoundInCallable(Object that, CallableMethod callable) {
		
		Field[] fields = callable.getClass().getDeclaredFields();
		int f = fields.length;
		while (--f>=0) {
			
			try {
				fields[f].setAccessible(true);
				if (fields[f].get(callable) == that) {
					return true;
				}
			} catch (IllegalArgumentException e) {
				throw new RuntimeException(e);
			} catch (IllegalAccessException e) {
				throw new RuntimeException(e);
			}
		}
		return false;
	}
	
	public final <T extends Behavior> ListenerFilter addSubscription(
				CharSequence topic, 
				CallableStaticMethod<T> method) {
		
		if (null == methods) {
			methodLookup = new TrieParser(16,1,false,false,false);
			methodReader = new TrieParserReader(0, true);
			methods = new CallableStaticMethod[0];
		}
		
		if (!startupCompleted && listener instanceof PubSubListener) {
			builder.addStartupSubscription(topic, System.identityHashCode(listener));
			toStringDetails = toStringDetails+"sub:'"+topic+"'\n";
		} else {
			if (startupCompleted) {
	    		throw new UnsupportedOperationException("Method dispatch subscritpions may not be modified at runtime.");
	    	}
		}
		
		int id = methods.length;	
		methodLookup.setUTF8Value(topic,id);
		//grow the array of methods to be called
		CallableStaticMethod[] newArray = new CallableStaticMethod[id+1];
		System.arraycopy(methods, 0, newArray, 0, id);
		newArray[id] = method;
		methods = newArray;
		//
		return this;
	}
	
	@Override
	public final ListenerFilter addSubscription(CharSequence topic) {		
		if (!startupCompleted && listener instanceof PubSubListener) {
			builder.addStartupSubscription(topic, System.identityHashCode(listener));		
			
			toStringDetails = toStringDetails+"sub:'"+topic+"'\n";
								
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

	//used for looking up the features used by this TrafficOrder goPipe
	private GatherAllFeatures ccmwp = new GatherAllFeatures();

	public int getFeatures(Pipe<TrafficOrderSchema> pipe) {
		ccmwp.init(pipe);
		ChildClassScanner.visitUsedByClass(listener, ccmwp, MsgCommandChannel.class);		
		return ccmwp.features();
	}
    
    
}
