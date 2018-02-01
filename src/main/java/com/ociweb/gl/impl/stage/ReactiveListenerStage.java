package com.ociweb.gl.impl.stage;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.gl.api.Behavior;
import com.ociweb.gl.api.HTTPFieldReader;
import com.ociweb.gl.api.HTTPRequestReader;
import com.ociweb.gl.api.HTTPResponseListener;
import com.ociweb.gl.api.HTTPResponseReader;
import com.ociweb.gl.api.HTTPSession;
import com.ociweb.gl.api.ListenerFilter;
import com.ociweb.gl.api.MsgCommandChannel;
import com.ociweb.gl.api.PubSubListener;
import com.ociweb.gl.api.RestListener;
import com.ociweb.gl.api.ShutdownListener;
import com.ociweb.gl.api.StartupListener;
import com.ociweb.gl.api.StateChangeListener;
import com.ociweb.gl.api.TimeListener;
import com.ociweb.gl.api.transducer.StartupListenerTransducer;
import com.ociweb.gl.impl.BuilderImpl;
import com.ociweb.gl.impl.ChildClassScanner;
import com.ociweb.gl.impl.ChildClassScannerVisitor;
import com.ociweb.gl.impl.http.server.HTTPResponseListenerBase;
import com.ociweb.gl.impl.PayloadReader;
import com.ociweb.gl.impl.PrivateTopic;
import com.ociweb.gl.impl.PubSubListenerBase;
import com.ociweb.gl.impl.PubSubMethodListenerBase;
import com.ociweb.gl.impl.RestMethodListenerBase;
import com.ociweb.gl.impl.StartupListenerBase;
import com.ociweb.gl.impl.schema.MessagePrivate;
import com.ociweb.gl.impl.schema.MessageSubscription;
import com.ociweb.gl.impl.schema.TrafficOrderSchema;
import com.ociweb.pronghorn.network.config.HTTPRevision;
import com.ociweb.pronghorn.network.config.HTTPSpecification;
import com.ociweb.pronghorn.network.config.HTTPVerb;
import com.ociweb.pronghorn.network.config.HTTPVerbDefaults;
import com.ociweb.pronghorn.network.schema.HTTPRequestSchema;
import com.ociweb.pronghorn.network.schema.NetResponseSchema;
import com.ociweb.pronghorn.pipe.ChannelReader;
import com.ociweb.pronghorn.pipe.DataInputBlobReader;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeConfig;
import com.ociweb.pronghorn.pipe.PipeUTF8MutableCharSquence;
import com.ociweb.pronghorn.pipe.util.hash.IntHashTable;
import com.ociweb.pronghorn.stage.PronghornStage;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;
import com.ociweb.pronghorn.util.TrieParser;
import com.ociweb.pronghorn.util.TrieParserReader;

public class ReactiveListenerStage<H extends BuilderImpl> extends PronghornStage implements ListenerFilter {

    private static final int SIZE_OF_PRIVATE_MSG_PUB = Pipe.sizeOf(MessagePrivate.instance, MessagePrivate.MSG_PUBLISH_1);
	private static final int SIZE_OF_MSG_STATECHANGE = Pipe.sizeOf(MessageSubscription.instance, MessageSubscription.MSG_STATECHANGED_71);
	private static final int SIZE_OF_MSG_PUBLISH = Pipe.sizeOf(MessageSubscription.instance, MessageSubscription.MSG_PUBLISH_103);
	protected final Behavior            listener;
    protected final TimeListener        timeListener;
    
    protected final Pipe<?>[]           inputPipes;
    protected final Pipe<?>[]           outputPipes;
        
    protected long                      timeTrigger;
    protected long                      timeRate;   
    
    protected H			        		builder;
  
    private static final Logger logger = LoggerFactory.getLogger(ReactiveListenerStage.class); 
     
    protected boolean startupCompleted;
    protected boolean shutdownCompleted;
    private boolean shutdownInProgress;
    
    //all non shutdown listening reactors will be shutdown only after the listeners have finished.
    protected static AtomicInteger liveShutdownListeners = new AtomicInteger();
    protected static AtomicInteger totalLiveReactors = new AtomicInteger();    
    protected static AtomicBoolean shutdownRequsted = new AtomicBoolean(false);
    protected static Runnable lastCall;
   
    ///////////////////////////
    private int httpClientPipeId = Integer.MIN_VALUE; ///unused

	private static final int MAX_HTTP_CLIENT_ID = ((1<<30)-1);

	///////////////////
	//only used for direct method dispatch upon subscription topic arrival
	//////////////////
	private TrieParser methodLookup;
	private TrieParserReader methodReader;
	private CallableStaticMethod[] methods;
	//////////////////
	
	private final PrivateTopic[] receivePrivateTopics;
	
	public final PublishPrivateTopics publishPrivateTopics;

	
	//////////////////
	//only used for direct rest response dispatch upon route arrival
	//////////////////
	private CallableStaticRestRequestReader[] restRequestReader;
	////////////////
	
	////////////////
	//only use for direct http query response dispatch upon arrival
	////////////////
	private CallableStaticHTTPResponse[] httpResponseReader;
	////////////////	
	
	    	
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

    private PipeUTF8MutableCharSquence mutableTopic = new PipeUTF8MutableCharSquence();
    private PayloadReader payloadReader;
    
    private HTTPSpecification httpSpec;
    private IntHashTable headerToPositionTable; //for HTTPClient
    private TrieParser headerTrieParser; //for HTTPClient
       
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
    		                     H builder, int parallelInstance, String nameId) {
        
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

        //only create private topics for named behaviors
        if (null!=nameId) {
        	List<PrivateTopic> listOut = builder.getPrivateTopicsFromTarget(nameId);
     
        	//only lookup topics if the builder knows of some
        	if (!listOut.isEmpty()) {
        		
		        int i = inputPipes.length;
		        this.receivePrivateTopics = new PrivateTopic[i];
		        while (--i>=0) {
		   
		        	if ( Pipe.isForSchema(inputPipes[i], MessagePrivate.instance) ) {
		        		
		        		int j = listOut.size();
		        		while(--j>=0) {
		        			if ( listOut.get(j).getPipe() == inputPipes[i] ) {
		        				this.receivePrivateTopics[i] = listOut.get(j);
		        				break;//done		        				
		        			}
		        		}
		        		assert (j>=0) : "error: did not find matching pipe for private topic";
		        	}		        	
		        }
		        
        	} else {
        		this.receivePrivateTopics = null;
        	}
	        ////////////////
        	
	        List<PrivateTopic> listIn = builder.getPrivateTopicsFromSource(nameId);
	  
	        if (!listIn.isEmpty()) {
		        	        	
	        	TrieParser privateTopicsPublishTrie;
	        	Pipe<MessagePrivate>[] privateTopicPublishPipes;
	        	TrieParserReader privateTopicsTrieReader;
	        	
	        	
	        	int i = listIn.size();
		        privateTopicsPublishTrie = new TrieParser(i,1,false,false,false);//a topic is case-sensitive
		        privateTopicPublishPipes = new Pipe[i];
		        while (--i >= 0) {
		        	PrivateTopic topic = listIn.get(i);
		        	//logger.info("set private topic for use {} {}",i,topic.topic);
		        	privateTopicPublishPipes[i] = topic.getPipe();
		        	privateTopicsPublishTrie.setUTF8Value(topic.topic, i);//to matching pipe index	
		        }
		        privateTopicsTrieReader = new TrieParserReader(0,true);
		        
		        this.publishPrivateTopics =
			        new PublishPrivateTopics(privateTopicsPublishTrie,
							        		privateTopicPublishPipes,
							        		privateTopicsTrieReader);
		        
	        } else {
	        	this.publishPrivateTopics = null;
	        }
	        
        } else {
        	this.receivePrivateTopics = null;
        	this.publishPrivateTopics = null;
        }
        
        int totalCount = totalLiveReactors.incrementAndGet();
        assert(totalCount>=0);
        
        if (listener instanceof ShutdownListener) {
        	toStringDetails = toStringDetails+"ShutdownListener\n";
        	int shudownListenrCount = liveShutdownListeners.incrementAndGet();
        	assert(shudownListenrCount>=0);
        }
        
        if (listener instanceof TimeListener) {
        	toStringDetails = toStringDetails+"TimeListener\n";
        	timeListener = (TimeListener)listener;
        	//time listeners are producers by definition
        	GraphManager.addNota(graphManager, GraphManager.PRODUCER, GraphManager.PRODUCER, this);
        	
        } else {
        	timeListener = null;
        }   
        
        if (listener instanceof StartupListener) {
        	toStringDetails = toStringDetails+"StartupListener\n";
        }
        
        GraphManager.addNota(graphManager, GraphManager.DOT_BACKGROUND, "burlywood2", this);
        
        
    }
        
	public void configureHTTPClientResponseSupport(int httpClientPipeId) {
		this.httpClientPipeId = httpClientPipeId;
	}

    private static Pipe[] consumerJoin(Pipe<?>[] inputPipes,
    		                   Iterator<ReactiveManagerPipeConsumer> iterator) {
    	if (iterator.hasNext()) {    		
    		return consumerJoin(join(inputPipes, iterator.next().inputs),iterator);    		
    	} else {
    		return inputPipes;
    	}
    }

    
    public static ReactiveOperators reactiveOperators() {
		return new ReactiveOperators()
				                .addOperator(PubSubMethodListenerBase.class, 
				                	 MessagePrivate.instance,
				               		 new ReactiveOperator() {
									@Override
									public void apply(int index, Object target, Pipe input, ReactiveListenerStage r) {
										r.consumePrivateMessage(index, target, input);										
									}        		                	 
				                 })
        		                 .addOperator(PubSubMethodListenerBase.class, 
        		                		 MessageSubscription.instance,
        		                		 new ReactiveOperator() {
									@Override
									public void apply(int index, Object target, Pipe input, ReactiveListenerStage r) {
										r.consumePubSubMessage(target, input);										
									}        		                	 
        		                 })
        		                 .addOperator(HTTPResponseListenerBase.class, 
        		                		 NetResponseSchema.instance,
        		                		 new ReactiveOperator() {
 									@Override
 									public void apply(int index, Object target, Pipe input, ReactiveListenerStage r) {
 										r.consumeNetResponse(target, input);										
 									}        		                	 
         		                 })
        		                 .addOperator(RestMethodListenerBase.class, 
        		                		 HTTPRequestSchema.instance,
        		                		 new ReactiveOperator() {
 									@Override
 									public void apply(int index, Object target, Pipe input, ReactiveListenerStage r) {
 										r.consumeRestRequest(target, input);										
 									}        		                	 
         		                 });
	}

    
    public static boolean isShutdownRequested() {
    	return shutdownRequsted.get();
    }
    
    public static void requestSystemShutdown(Runnable shutdownRunnable) {
    	lastCall = shutdownRunnable;
    	shutdownRequsted.set(true);
    	
    	
    	//logger.info("shutdown requested");
    }


	protected String toStringDetails = "\n";
    public String toString() {
    	String parent = super.toString();
    	
    	String behaviorName = null==listener ? "Unknown Behavior" :
    		listener.getClass().getSimpleName().trim();
    	
    	if (behaviorName.length()>0) {
    		parent = parent.substring(getClass().getSimpleName().length(), parent.length());    		
    	}
    	
		return behaviorName+parent+toStringDetails;    	
    }
    
    public final void setTimeEventSchedule(long rate, long start) {
        
        timeRate = rate;
        timeTrigger = start;

        timeEvents = (0 != timeRate) && (listener instanceof TimeListener);
    }
    
    
    protected ChildClassScannerVisitor visitAllStartups = new ChildClassScannerVisitor<StartupListenerTransducer>() {

		@Override
		public boolean visit(StartupListenerTransducer child, Object topParent) {
			runStartupListener(child);
			return true;
		}

    	
    };
    

    
    @Override
    public void startup() {              
 
    	//////////////////////////////////////////////////////////////////
    	//ALL operators have been added to operators so it can be used to create consumers as needed
    	consumer = new ReactiveManagerPipeConsumer(listener, builder.operators, inputPipes);
    	    	
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
         
        //does all the transducer startup listeners first
    	ChildClassScanner.visitUsedByClass( listener, 
    										visitAllStartups, 
    										StartupListenerTransducer.class);//populates outputPipes

        
        //Do last so we complete all the initializations first
        if (listener instanceof StartupListener) {
        	runStartupListener((StartupListenerBase)listener);
        }        
        startupCompleted=true;
        
    }

	private void runStartupListener(StartupListenerBase startupListener) {
		long start = System.currentTimeMillis();
		startupListener.startup();
		long duration = System.currentTimeMillis()-start;
		if (duration>40) { //human perception
			String name = listener.getClass().getSimpleName().trim();
			if (name.length() == 0) {
				name = "a startup listener lambda";
			}
			logger.warn(
					"WARNING: startup method for {} took over {} ms. "+
			        "Reconsider the design you may want to do this work in a message listener.\n"+
					"Note that no behaviors will execute untill all have completed their startups.",
					name, duration);        		      		
		}
	}

    @Override
    public void run() {
        
    	if (!shutdownInProgress) {
	    	if (shutdownRequsted.get()) {
	    		if (!shutdownCompleted) {
	    			
	    			if (listener instanceof ShutdownListener) {    				
	    				if (((ShutdownListener)listener).acceptShutdown()) {
	    					int remaining = liveShutdownListeners.decrementAndGet();
	    					assert(remaining>=0);
	    					shutdownInProgress = true;
	    					return;
	    				}
	    				//else continue with normal run processing
	    				
	    			} else {
	    				//this one is not a listener so we must wait for all the listeners to close first
	    				
	    				if (0 == liveShutdownListeners.get()) {    					
	    					shutdownInProgress = true;
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
				processTimeEvents(timeListener, timeTrigger);            
			}
	        
	        //behaviors
	        consumer.process(this);
	        
	        //all transducers
	        int j = consumers.size();
	        while(--j>=0) {
	        	consumers.get(j).process(this);
	        }
	  
    	} else {
    		//shutdown in progress logic
    		int i = outputPipes.length;    		
    		while (--i>=0) {
    			if ((null!=outputPipes[i]) && !Pipe.hasRoomForWrite(outputPipes[i], Pipe.EOF_SIZE)) {
    				return;//must wait for pipe to empty
    			}
    		}		
    		//now free to shut down, we know there is room to do so.
    		requestShutdown();
    		return;
    	}
    }


	@Override    
    public void shutdown() {
		
		assert(!shutdownCompleted) : "already shut down why was this called a second time?";

		Pipe.publishEOF(outputPipes);
				

		if (totalLiveReactors.decrementAndGet()==0) {
			//ready for full system shutdown.
			if (null!=lastCall) {				
				new Thread(lastCall).start();
			}
		}
		shutdownCompleted = true;
    }

    
    final void consumeRestRequest(Object listener, Pipe<HTTPRequestSchema> p) {
		
    	  while (Pipe.hasContentToRead(p)) {                
              
    		  Pipe.markTail(p);             
              int msgIdx = Pipe.takeMsgIdx(p);
    	  
    	      if (HTTPRequestSchema.MSG_RESTREQUEST_300==msgIdx) {
    	    	 
    	    	  long connectionId = Pipe.takeLong(p);
    	    	  final int sequenceNo = Pipe.takeInt(p);    	    	  

    	    	  int routeVerb = Pipe.takeInt(p);
    	    	  int routeId = routeVerb>>>HTTPVerb.BITS;
    	    	  int verbId = HTTPVerb.MASK & routeVerb;
    	    	  
    	    	      	    	  
    	    	  HTTPRequestReader reader = (HTTPRequestReader)Pipe.openInputStream(p);
    	        	    	    	  
 				  reader.setParseDetails( builder.routeExtractionParser(routeId),
 						                  builder.routeHeaderToPositionTable(routeId), 
 						                  builder.routeExtractionParserIndexCount(routeId),
 						                  builder.routeHeaderTrieParser(routeId),
 						                  builder.httpSpec
 						                 );
 				  
    	    	  int parallelRevision = Pipe.takeInt(p);
    	    	  int parallelIdx = parallelRevision >>> HTTPRevision.BITS;
    	    	  int revision = HTTPRevision.MASK & parallelRevision;
    	    	  
				  reader.setRevisionId(revision);
    	    	  reader.setRequestContext(Pipe.takeInt(p));  
    	    
    	    	  reader.setRouteId(routeId);
    	    	  
    	    	  //both these values are required in order to ensure the right sequence order once processed.
    	    	  long sequenceCode = (((long)parallelIdx)<<32) | ((long)sequenceNo);
    	    	  
    	    	  //System.err.println("Reader is given seuqence code of "+sequenceNo);
    	    	  reader.setConnectionId(connectionId, sequenceCode);
    	    	  
    	    	  //assign verbs as strings...
    	    	  reader.setVerb((HTTPVerbDefaults)httpSpec.verbs[verbId]);
 			
    	    	  if (null!=restRequestReader && 
    	    	      routeId<restRequestReader.length &&
    	    	      null!=restRequestReader[routeId]) {
    	    		  
    	    		  if (!restRequestReader[routeId].restRequest(listener, reader)) {
    	    			  Pipe.resetTail(p);
		            	  return;//continue later and repeat this same value.
    	    		  }
    	    		  
    	    	  } else {
    	    		  if (listener instanceof RestListener) {
    	    			  
		    	    	  if (!((RestListener)listener).restRequest(reader)) {
			            		 Pipe.resetTail(p);
			            		 return;//continue later and repeat this same value.
			              }
    	    		  }
    	    	  }

    	      } else {
    	    	  logger.error("unrecognized message on {} ",p);
    	    	  throw new UnsupportedOperationException("unexpected message "+msgIdx);
    	      }
        
    	      Pipe.confirmLowLevelRead(p, Pipe.sizeOf(p,msgIdx));
              Pipe.releaseReadLock(p);
              
    	  }
    	   	
    	
	}

    
    
	final void consumeNetResponse(Object listener, Pipe<NetResponseSchema> p) {

    	 while (Pipe.hasContentToRead(p)) {                
             
       		 Pipe.markTail(p);
    		 
             int msgIdx = Pipe.takeMsgIdx(p);
             
             //logger.info("response from HTTP request. Type is {} ",msgIdx);
             
             switch (msgIdx) {
	             case NetResponseSchema.MSG_RESPONSE_101:

	            	 long ccId1 = Pipe.takeLong(p);
	            	 int flags = Pipe.takeInt(p);
	            	 
	            	 //NOTE: this HTTPResponseReader object will show up n times in a row until
	            	 //      the full file is complete.  No files will be interleaved.
            		 HTTPResponseReader reader = (HTTPResponseReader)Pipe.inputStream(p);
	            	 reader.openLowLevelAPIField();
	            	 
	            	 //logger.trace("running position {} ",reader.absolutePosition());
	
	            	 final short statusId = reader.readShort();	
				     reader.setParseDetails(headerToPositionTable, headerTrieParser, builder.httpSpec);

				     reader.setStatusCode(statusId);
				     
				     reader.setConnectionId(ccId1);
				     
				     //logger.trace("data avail {} status {} ",reader.available(),statusId);
				     
            	 	            	 
	            	 reader.setFlags(flags);
	        
	            	 
	            	 //TODO: map calls to diferent methods?
	            	 //      done by status code?
	            	 //TODO: can we get the call latency??
	            	 
	            	 if (!((HTTPResponseListener)listener).responseHTTP(reader)) {
	            		 Pipe.resetTail(p);
	            		 //logger.info("CONTINUE LATER");
	            		 return;//continue later and repeat this same value.
	            	 }
	                 
	            	 
	            	 break;
	             case NetResponseSchema.MSG_CONTINUATION_102:
	            	 long fieldConnectionId = Pipe.takeLong(p);
	            	 int flags2 = Pipe.takeInt(p);
	            	 
            		 HTTPResponseReader continuation = (HTTPResponseReader)Pipe.inputStream(p);
            		 continuation.openLowLevelAPIField();
            		 continuation.setFlags(flags2);
            		 
            		 //logger.trace("continuation with "+Integer.toHexString(flags2)+" avail "+continuation.available());
            		 
	            	 if (!((HTTPResponseListener)listener).responseHTTP(continuation)) {
						 Pipe.resetTail(p);
						 return;//continue later and repeat this same value.
					 }
            		 
	            	 break;
	             case NetResponseSchema.MSG_CLOSED_10:

	            	 HTTPResponseReader hostReader = (HTTPResponseReader)Pipe.inputStream(p);
	            	 hostReader.openLowLevelAPIField();
	            	 
	            	 hostReader.setFlags(HTTPFieldReader.END_OF_RESPONSE | 
	            			             HTTPFieldReader.CLOSE_CONNECTION);
	            	 
	            	 int port = Pipe.takeInt(p);//the caller does not care which port we were on.
					   
	            	 if (!((HTTPResponseListener)listener).responseHTTP(hostReader)) {
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

	final void consumePrivateMessage(int index, Object listener, Pipe<MessagePrivate> p) {
		
		final PrivateTopic topic = receivePrivateTopics[index];
		assert (null!=topic);
		assert (null!=listener);
		
		while (Pipe.hasContentToRead(p)) {
					
				Pipe.markTail(p);
				
	            final int msgIdx = Pipe.takeMsgIdx(p);             		            
	            assert(MessagePrivate.MSG_PUBLISH_1 == msgIdx);
	            
	            int dispatch = -1; //TODO: move all these topic lookups to be done once and stored under index..
	            
                if (((null==methodReader) 
                	|| ((dispatch=(int)TrieParserReader.query(methodReader, methodLookup, topic.topic))<0))
                	&& ((listener instanceof PubSubListenerBase))) {
                	
                	if (! ((PubSubListenerBase)listener).message(topic.topic, Pipe.openInputStream(p))) {
                		Pipe.resetTail(p);
	            		return;//continue later and repeat this same value.
                	}
                	
                } else {
                	if (! methods[dispatch].method(listener, topic.topic, Pipe.openInputStream(p))) {
                		Pipe.resetTail(p);
                		return;//continue later and repeat this same value.	                    		
                	}
                }
	           
	            
	            Pipe.confirmLowLevelRead(p, SIZE_OF_PRIVATE_MSG_PUB);
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
	                    	                           
	                    int dispatch = -1;
	                    
	                    if (((null==methodReader) 
	                    	|| ((dispatch=methodLookup(p, len, pos))<0))
	                    	&& ((listener instanceof PubSubListenerBase))) {
	                    	
	                    	if (! ((PubSubListenerBase)listener).message(mutableTopic,reader)) {
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
                	shutdownInProgress = true;
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
				Thread.currentThread().interrupt();
			}
		}		

		long now = System.nanoTime();
		
		int iteration = timeIteration++;		
		listener.timeEvent(trigger, iteration);
		
		long duration = (System.nanoTime()-now)/MS_to_NS;
		
		if (duration>timeRate) {
			logger.warn("time pulse is scheduled at a rate of {}ms "
				 	  + "however the last time event call took {}ms which is too long."
				 	  + " \nConsider doing less work in the timeEvent() method, use publishTopic() "
				 	  + "to push this work off till later.", timeRate, duration);
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
		
		if (listener instanceof RestMethodListenerBase) {
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
	
	@Override
	public final ListenerFilter excludeRoutes(int... routeIds) {

		if (listener instanceof RestListener) {
			int count = 0;
			int i =	inputPipes.length;
			while (--i>=0) {
				//we only expect to find a single request pipe
				if (Pipe.isForSchema(inputPipes[i], HTTPRequestSchema.class)) {		
				  
					int allRoutes = builder.routerConfig().routesCount();	
					int p = parallelInstance==-1?count:parallelInstance;
					while (--allRoutes>=0) {
						if (!contains(routeIds,allRoutes)) {
							restRoutesDefined = true;
							builder.appendPipeMapping((Pipe<HTTPRequestSchema>) inputPipes[i], allRoutes, p);
						}
					}
					count++;
				}
			}
			return this;
		} else {
			throw new UnsupportedOperationException("The Listener must be an instance of "+RestListener.class.getSimpleName()+" in order to call this method.");
		}
	}
	
	private boolean contains(int[] array, int item) {
		int i = array.length;
		while (--i>=0) {
			if (array[i] == item) {
				return true;
			}
		}
		return false;
	}

	
//	@Override
//	public void includeHTTPClientId(int id) {
//		assert(id >= 0) : "Id must be zero or greater but less than "+MAX_HTTP_CLIENT_ID;
//		assert(id < MAX_HTTP_CLIENT_ID) : "Id must be less than or equal to "+MAX_HTTP_CLIENT_ID;		
//		builder.registerHTTPClientId(MAX_HTTP_CLIENT_ID&id, httpClientPipeId);
//	}

	@SuppressWarnings("unchecked")
	public final ListenerFilter addSubscription(CharSequence topic, 
		                                    	final CallableMethod callable) {
		
		return addSubscription(topic, new CallableStaticMethod() {
			@Override
			public boolean method(Object that, CharSequence title, ChannelReader reader) {
				//that must be found as the declared field of the lambda
				assert(childIsFoundIn(that,callable)) : "may only call methods on this same Behavior instance";
				return callable.method(title, reader);
			}
		});
	}
	
	public final ListenerFilter includeRoute(int routeId, final CallableRestRequestReader callable) {
		
		if (null==restRequestReader) {
			restRequestReader = new CallableStaticRestRequestReader[routeId+1];		
		} else {
			if (routeId>= restRequestReader.length) {
				CallableStaticRestRequestReader[] temp = new CallableStaticRestRequestReader[routeId+1];	
				System.arraycopy(restRequestReader, 0, temp, 0, restRequestReader.length);
				restRequestReader = temp;			
			}
		}
		
		restRequestReader[routeId] = new CallableStaticRestRequestReader() {			
			@Override
			public boolean restRequest(Object that, HTTPRequestReader request) {
				//that must be found as the declared field of the lambda
				assert(childIsFoundIn(that,callable)) : "may only call methods on this same Behavior instance";
				return callable.restRequest(request);
			}			
		};
		return this;
	}
	
	public final <T extends Behavior> ListenerFilter includeRoute(int routeId, final CallableStaticRestRequestReader<T> callable) {
		
		if (null==restRequestReader) {
			restRequestReader = new CallableStaticRestRequestReader[routeId+1];		
		} else {
			if (routeId>= restRequestReader.length) {
				CallableStaticRestRequestReader<T>[] temp = new CallableStaticRestRequestReader[routeId+1];	
				System.arraycopy(restRequestReader, 0, temp, 0, restRequestReader.length);
				restRequestReader = temp;			
			}
		}		
		restRequestReader[routeId] = callable;
		
		return this;
	}
    
    public int getId() {
    	return builder.behaviorId((Behavior)listener);
    }
	
	private boolean childIsFoundIn(Object child, Object parent) {
		
		Field[] fields = parent.getClass().getDeclaredFields();
		int f = fields.length;
		while (--f>=0) {
			
			try {
				fields[f].setAccessible(true);
				if (fields[f].get(parent) == child) {
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
		
		if (!startupCompleted && listener instanceof PubSubMethodListenerBase) {
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
		if (!startupCompleted && listener instanceof PubSubMethodListenerBase) {
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
	private GatherAllFeaturesAndSetReactor ccmwp = new GatherAllFeaturesAndSetReactor(this);
    private PrivateTopicReg privateTopicReg = new PrivateTopicReg(this);
	
	public int getFeatures(Pipe<TrafficOrderSchema> pipe) {
		//logger.info("getFeatuers was called, should visit all command channels");
		ccmwp.init(pipe);
		ChildClassScanner.visitUsedByClass(listener, ccmwp, MsgCommandChannel.class);		
		return ccmwp.features();
	}

	public void regPrivateTopics() {
		//logger.info("regPrivateTopics was called, should visit all command channels");
		ChildClassScanner.visitUsedByClass(listener, privateTopicReg, MsgCommandChannel.class);		
	}
	
	
	@Override
	public <E extends Enum<E>> ListenerFilter includeHTTPSession(HTTPSession... httpSessions) {
		
		int j = httpSessions.length;
		while(--j >= 0) {
			
			//TODO: big issue with lambdas inside the parallel code geting mixed !!!!
			
			//register listener will set these values before we use include
		    int pipeIdx = builder.lookupHTTPClientPipe(builder.behaviorId(listener));
		    //we added one more uniqueId to the same pipeIdx given this listeners id
		    builder.registerHTTPClientId(httpSessions[j].uniqueId, pipeIdx);   
		    logger.trace("register session {} with pipe {}",httpSessions[j].uniqueId,pipeIdx);
		}
		
		return this;
	}
    
    
}
