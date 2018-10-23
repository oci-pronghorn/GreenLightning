package com.ociweb.gl.impl.stage;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.gl.api.Behavior;
import com.ociweb.gl.api.ClientHostPortInstance;
import com.ociweb.gl.api.HTTPRequestReader;
import com.ociweb.gl.api.HTTPResponseListener;
import com.ociweb.gl.api.HTTPResponseReader;
import com.ociweb.gl.api.ListenerConfig;
import com.ociweb.gl.api.ListenerFilter;
import com.ociweb.gl.api.MsgCommandChannel;
import com.ociweb.gl.api.MsgRuntime;
import com.ociweb.gl.api.PubSubListener;
import com.ociweb.gl.api.RestListener;
import com.ociweb.gl.api.SerialStoreProducerAckListener;
import com.ociweb.gl.api.SerialStoreReleaseAckListener;
import com.ociweb.gl.api.SerialStoreReplayListener;
import com.ociweb.gl.api.ShutdownListener;
import com.ociweb.gl.api.StartupListener;
import com.ociweb.gl.api.StateChangeListener;
import com.ociweb.gl.api.TimeListener;
import com.ociweb.gl.api.transducer.StartupListenerTransducer;
import com.ociweb.gl.impl.BuilderImpl;
import com.ociweb.gl.impl.ChildClassScanner;
import com.ociweb.gl.impl.ChildClassScannerVisitor;
import com.ociweb.gl.impl.PayloadReader;
import com.ociweb.gl.impl.PrivateTopic;
import com.ociweb.gl.impl.PubSubListenerBase;
import com.ociweb.gl.impl.PubSubMethodListenerBase;
import com.ociweb.gl.impl.RestListenerBase;
import com.ociweb.gl.impl.RestMethodListenerBase;
import com.ociweb.gl.impl.StartupListenerBase;
import com.ociweb.gl.impl.TickListenerBase;
import com.ociweb.gl.impl.http.server.HTTPResponseListenerBase;
import com.ociweb.gl.impl.mqtt.EgressMQTTBehavior;
import com.ociweb.gl.impl.mqtt.IngressMQTTBehavior;
import com.ociweb.gl.impl.schema.MessagePrivate;
import com.ociweb.gl.impl.schema.MessageSubscription;
import com.ociweb.gl.impl.schema.TrafficOrderSchema;
import com.ociweb.json.JSONExtractorCompleted;
import com.ociweb.pronghorn.network.ClientCoordinator;
import com.ociweb.pronghorn.network.OrderSupervisorStage;
import com.ociweb.pronghorn.network.ServerCoordinator;
import com.ociweb.pronghorn.network.config.HTTPRevision;
import com.ociweb.pronghorn.network.config.HTTPSpecification;
import com.ociweb.pronghorn.network.config.HTTPVerb;
import com.ociweb.pronghorn.network.config.HTTPVerbDefaults;
import com.ociweb.pronghorn.network.http.NetResponseJSONExtractionStage;
import com.ociweb.pronghorn.network.mqtt.MQTTClientGraphBuilder;
import com.ociweb.pronghorn.network.schema.HTTPRequestSchema;
import com.ociweb.pronghorn.network.schema.NetResponseSchema;
import com.ociweb.pronghorn.pipe.ChannelReader;
import com.ociweb.pronghorn.pipe.DataInputBlobReader;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeConfig;
import com.ociweb.pronghorn.pipe.PipeUTF8MutableCharSquence;
import com.ociweb.pronghorn.pipe.util.hash.IntHashTable;
import com.ociweb.pronghorn.stage.PronghornStage;
import com.ociweb.pronghorn.stage.file.schema.PersistedBlobLoadConsumerSchema;
import com.ociweb.pronghorn.stage.file.schema.PersistedBlobLoadProducerSchema;
import com.ociweb.pronghorn.stage.file.schema.PersistedBlobLoadReleaseSchema;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;
import com.ociweb.pronghorn.stage.scheduling.NonThreadScheduler;
import com.ociweb.pronghorn.util.TrieParser;
import com.ociweb.pronghorn.util.TrieParserReader;

public class ReactiveListenerStage<H extends BuilderImpl> extends ReactiveProxy implements ListenerFilter, PendingStageBuildable {

    private static final int SIZE_OF_PRIVATE_MSG_PUB = Pipe.sizeOf(MessagePrivate.instance, MessagePrivate.MSG_PUBLISH_1);
	private static final int SIZE_OF_MSG_STATECHANGE = Pipe.sizeOf(MessageSubscription.instance, MessageSubscription.MSG_STATECHANGED_71);
	private static final int SIZE_OF_MSG_PUBLISH     = Pipe.sizeOf(MessageSubscription.instance, MessageSubscription.MSG_PUBLISH_103);
	
	protected final Behavior            listener;
    protected TimeListener              timeListener;
    
    protected Pipe<?>[]           inputPipes;
    protected Pipe<?>[]           outputPipes;
    
    protected long                      timeTrigger;
    protected long                      timeRate;   
    
    protected H			        		builder;
  
    private static final Logger logger = LoggerFactory.getLogger(ReactiveListenerStage.class); 
     
    protected boolean startupCompleted;
    protected boolean shutdownCompleted;
    private boolean shutdownInProgress;
 
	
	private int totalSupscriptions = 0;
	
   
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
	
	private PrivateTopic[] receivePrivateTopics;
	
	public PublishPrivateTopics publishPrivateTopics;

	
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
    protected final TickListenerBase tickListener;
    
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

    protected ReactiveManagerPipeConsumer consumer;

	protected static final long MS_to_NS = 1_000_000;
    private int timeIteration = 0;
    private int parallelInstance;
    
    private final ArrayList<ReactiveManagerPipeConsumer> consumers;
	private String behaviorName;
    
    protected ReactiveProxyStage realStage;

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

        this.listener = listener;
        assert(null!=listener) : "Behavior must be defined";
        this.parallelInstance = parallelInstance;
        this.consumers = consumers;
        this.inputPipes = inputPipes;
        this.outputPipes = outputPipes;       
        this.builder = builder;
                                  
      
        this.states = builder.getStates();
        this.graphManager = graphManager;

        int totalCount = builder.totalLiveReactors.incrementAndGet();
        assert(totalCount>=0);
                
        this.nameId=nameId;
        this.behaviorName = null!=nameId?builder.validateUniqueName(nameId, parallelInstance):null;
        
        builder.pendingInit(this);
        
        if (listener instanceof TickListenerBase) {
        	tickListener = (TickListenerBase)listener;
        } else {
        	tickListener = null;
        }
   
    }
    
    private final String nameId;
    private final ChildClassScannerVisitor<MsgCommandChannel> gatherPipesVisitor = new ChildClassScannerVisitor<MsgCommandChannel>() {
    	
    	@Override
    	public boolean visit(MsgCommandChannel cmdChnl, Object topParent, String topName) {
    		outputPipes = PronghornStage.join(outputPipes, cmdChnl.getOutputPipes());
    		return true;
    	}
    	
    };

	public void initRealStage() {
				
				
		if (null==this.realStage) {
			
			//extract pipes used by listener and use cmdChannelUsageChecker to confirm its not re-used
			//all the outputs are collected in outputPipes together, this can not be done any earler due to auto private topic feature
			//if auto private topics has found them all to be topic then pub sub is disabled since it is not needed.
			ChildClassScanner.visitUsedByClass(nameId, listener, gatherPipesVisitor, MsgCommandChannel.class);//populates outputPipes	
			
			
	        if ( (!this.builder.isAllPrivateTopics()) 
	        	&& this.builder.isListeningToSubscription(listener)) {
	        	
	        	//do not create this pipe if all the subscriptions have been detected as private subscriptions
	        	List<PrivateTopic> targetTopics = builder.getPrivateTopicsFromTarget(nameId);	        	
	        	if (targetTopics.size() != totalSupscriptions) {
		        	///this is done late because if we have detected that pub sub router is not required it creates fewer pipes.	
		    		builder.populateListenerIdentityHash(listener);
		        	inputPipes = PronghornStage.join(inputPipes, MsgRuntime.buildMessageSubscriptionPipe(builder) );
	        	}
	        }
						
			if (null!=behaviorName) {
				List<PrivateTopic> sourceTopics = builder.getPrivateTopicsFromSource(nameId);
				int i = sourceTopics.size();
				while (--i>=0) {
					PrivateTopic privateTopic = sourceTopics.get(i);
					outputPipes = PronghornStage.join(outputPipes, privateTopic.getPipe(parallelInstance));				
				}		
				List<PrivateTopic> targetTopics = builder.getPrivateTopicsFromTarget(nameId);
								
				int j = targetTopics.size();
				while (--j>=0) {
					PrivateTopic privateTopic = targetTopics.get(j);
					inputPipes = PronghornStage.join(inputPipes, privateTopic.getPipe(parallelInstance));
				}
	        	List<PrivateTopic> privateTopicList = builder.getPrivateTopicsFromTarget(nameId);
	            
				
				logger.trace("setting stage name: {}",this.behaviorName);

	        	//only lookup topics if the builder knows of some
	        	if (!privateTopicList.isEmpty()) {
	        		
			        i = inputPipes.length;
			        this.receivePrivateTopics = new PrivateTopic[inputPipes.length];		        					
			        while (--i>=0) {
			   
			        	if ( Pipe.isForSchema(inputPipes[i], MessagePrivate.instance) ) {
			        		
			        		j = privateTopicList.size();
			        		while(--j>=0) {
			        			PrivateTopic privateTopic = privateTopicList.get(j);	
			        			
								if ( privateTopic.getPipe(parallelInstance) == (inputPipes[i])) {	        				
			        				this.receivePrivateTopics[i] = privateTopic;
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
		        	TrieParserReader privateTopicsTrieReader;
		        	
		        	
		            i = listIn.size();
			        privateTopicsPublishTrie = new TrieParser(i,1,false,false,false);//a topic is case-sensitive
			        Pipe<MessagePrivate>[] privateTopicPublishPipes = new Pipe[i];
			        String[] topics = new String[i];
			        while (--i >= 0) {
			        	PrivateTopic topic = listIn.get(i);
			        	//logger.info("set private topic for use {} {}",i,topic.topic);
			        	privateTopicPublishPipes[i] = topic.getPipe(parallelInstance);
			        	topics[i] = topic.topic;
			        	privateTopicsPublishTrie.setUTF8Value(topic.topic, i);//to matching pipe index				
			        }
			        privateTopicsTrieReader = new TrieParserReader(true);
			        
			        this.publishPrivateTopics =
				        new PublishPrivateTopics(privateTopicsPublishTrie,
								        		privateTopicPublishPipes,
								        		privateTopicsTrieReader,topics);
			        
		        } else {
		        	this.publishPrivateTopics = null;
		        }
		        
							
			}

			this.realStage = new ReactiveProxyStage(this, graphManager, consumerJoin(inputPipes, consumers.iterator()), outputPipes);
			
	        if (listener instanceof ShutdownListener) {
	        	toStringDetails = toStringDetails+"ShutdownListener\n";
	        	int shudownListenrCount = builder.liveShutdownListeners.incrementAndGet();
	        	assert(shudownListenrCount>=0);
	        }
	        

	
	        GraphManager.addNota(graphManager, GraphManager.STAGE_NAME,
					this.behaviorName, this.realStage);
	    	
	        if (listener instanceof TimeListener) {
	        	toStringDetails = toStringDetails+"TimeListener\n";
	        	timeListener = (TimeListener)listener;
	        	//time listeners are producers by definition
	        	GraphManager.addNota(graphManager, GraphManager.PRODUCER, GraphManager.PRODUCER, this.realStage);
	        	
	        } else {
	        	timeListener = null; 
	        }   
	        
	        if (listener instanceof StartupListener) {
	        	toStringDetails = toStringDetails+"StartupListener\n";
	        }
	        
	        GraphManager.addNota(graphManager, GraphManager.DOT_BACKGROUND, "burlywood2", this.realStage);
	        
	        if (listener instanceof RestListenerBase) {
				GraphManager.addNota(graphManager, GraphManager.DOT_RANK_NAME, "ModuleStage", this.realStage);
				
			} else if (listener instanceof IngressMQTTBehavior) {
	        	GraphManager.addNota(graphManager, GraphManager.DOT_RANK_NAME, "SocketReader", this.realStage);
	        	GraphManager.addNota(graphManager, GraphManager.DOT_BACKGROUND, MQTTClientGraphBuilder.BACKGROUND_COLOR, this.realStage);
	        } else if (listener instanceof EgressMQTTBehavior) {
	        	GraphManager.addNota(graphManager, GraphManager.DOT_RANK_NAME, "SocketWriter", this.realStage);
	        	GraphManager.addNota(graphManager, GraphManager.DOT_BACKGROUND, MQTTClientGraphBuilder.BACKGROUND_COLOR, this.realStage);
	        }
	        
	        
	        
	        if (isolate) {
	    		GraphManager.addNota(graphManager, 
	    				GraphManager.ISOLATE, 
	    				GraphManager.ISOLATE, 
	    				this.realStage.stageId);
	        }
	        
	        if (producer) {
	     		GraphManager.addNota(graphManager, 
	    				GraphManager.PRODUCER, 
	    				GraphManager.PRODUCER, 
	    				this.realStage.stageId);
	        }
	        
	        if (slaLatency>=0) {
	    		GraphManager.addNota(graphManager, 
			             GraphManager.SLA_LATENCY,
			             slaLatency, this.realStage);
	        }
	        configureStageRate();
	        

	        //finds all the command channels which make use of private topics.
	        regPrivateTopics();
	        
		}
	}
	

    
    protected static final int nsPerMS = 1_000_000;
    
    private void configureStageRate() {
        //if we have a time event turn it on.
        long rate = builder.getTriggerRate();
        if (rate>0 && listener instanceof TimeListener) {
            setTimeEventSchedule(rate, builder.getTriggerStart());
            //Since we are using the time schedule we must set the stage to be faster
            long customRate =   (rate*nsPerMS)/NonThreadScheduler.granularityMultiplier;// in ns and guanularityXfaster than clock trigger
            long appliedRate = Math.min(customRate,builder.getDefaultSleepRateNS());
            GraphManager.addNota(graphManager, GraphManager.SCHEDULE_RATE, appliedRate, realStage);
        }
    }
        
	public void configureHTTPClientResponseSupport(int httpClientPipeId) {
		this.httpClientPipeId = httpClientPipeId;
	}

	private boolean isolate = false;
	private boolean producer = false;
	private long slaLatency = -1;
	
	@Override
	public ListenerFilter isolate() {
		isolate = true;
		return this;
	}

	protected ListenerFilter producer() {
		producer = true;
		return this;
	}
	
	@Override
	public ListenerFilter SLALatencyNS(long latency) {
		slaLatency = latency;
		return this;
	}
	
	
	/**
	 *
	 * @param inputPipes Pipe<?> arg used in consumerJoin
	 * @param iterator Iterator<ReactiveManagerPipeConsumer> arg used with .hasNext()
	 * @return consumerJoin(join(inputPipes, iterator.next().inputs),iterator) if iterator.hasNext() else inputPipes
	 */
    private static Pipe[] consumerJoin(Pipe<?>[] inputPipes,
    		                   Iterator<ReactiveManagerPipeConsumer> iterator) {
    	if (iterator.hasNext()) {    		
    		return consumerJoin(PronghornStage.join(inputPipes, iterator.next().inputs),iterator);    		
    	} else {
    		return inputPipes;
    	}
    }

    
    public static ReactiveOperators reactiveOperators() {
		return new ReactiveOperators()
	
				                .addOperator(SerialStoreReplayListener.class,				                		
				                		PersistedBlobLoadConsumerSchema.instance,					                	 
					               		 new ReactiveOperator() {
										@Override
										public void apply(int index, Object target, Pipe input, ReactiveListenerStage r) {
											r.consumeStoreReplay(index, target, input);										
										}        		                	 
					                 })
				                .addOperator(SerialStoreReleaseAckListener.class,				                		
				                		PersistedBlobLoadReleaseSchema.instance,					                	 
					               		 new ReactiveOperator() {
										@Override
										public void apply(int index, Object target, Pipe input, ReactiveListenerStage r) {
											r.consumeStoreReleaseAck(index, target, input);										
										}        		                	 
					                 })
				                .addOperator(SerialStoreProducerAckListener.class,				                		
				                		PersistedBlobLoadProducerSchema.instance,					                	 
					               		 new ReactiveOperator() {
										@Override
										public void apply(int index, Object target, Pipe input, ReactiveListenerStage r) {
											r.consumeStoreWriteAck(index, target, input);										
										}        		                	 
					                 })
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

    
    protected void consumeStoreWriteAck(int index, Object target, Pipe<PersistedBlobLoadProducerSchema> p) {
	   	
		final int store = IntHashTable.getItem(serialStoreProdAckPipeMap, NON_ZERO_BASE+p.id)-NON_ZERO_BASE;
		    	
    	while (Pipe.hasContentToRead(p)) {                
	         
	   		 Pipe.markTail(p);
			 
	         int msgIdx = Pipe.takeMsgIdx(p);
	         
	         if (msgIdx  == PersistedBlobLoadProducerSchema.MSG_ACKWRITE_11) {
	        	 
	        	 long value = Pipe.takeLong(p);
	        	 
	        	 if (!((SerialStoreProducerAckListener)target).producerAck(store, value)) {
            		 Pipe.resetTail(p);
            		 return;//continue later and repeat this same value.
	        	 }
	        	 
	         } else {
	          	  logger.error("unrecognized message on {} ",p);
    	    	  throw new UnsupportedOperationException("unexpected message "+msgIdx);
	         }
	         
   	      	 Pipe.confirmLowLevelRead(p, Pipe.sizeOf(p,msgIdx));
             Pipe.releaseReadLock(p);
	   	 }
	}

	protected void consumeStoreReleaseAck(int index, Object target, Pipe<PersistedBlobLoadReleaseSchema> p) {
	   	
		  final int store = IntHashTable.getItem(serialStoreRelAckPipeMap, NON_ZERO_BASE+p.id)-NON_ZERO_BASE;
				
		  while (Pipe.hasContentToRead(p)) { 
	   		 Pipe.markTail(p);
	         int msgIdx = Pipe.takeMsgIdx(p);
	         
	         if (msgIdx  == PersistedBlobLoadReleaseSchema.MSG_ACKRELEASE_10) {
	        	 
	        	 long value = Pipe.takeLong(p);
	        	 
	        	 if (!((SerialStoreReleaseAckListener)target).releaseAck(store, value)) {
            		 Pipe.resetTail(p);
            		 return;//continue later and repeat this same value.
	        	 }
	        	 
	         } else {
	          	  logger.error("unrecognized message on {} ",p);
    	    	  throw new UnsupportedOperationException("unexpected message "+msgIdx);
	         }
	         
   	      	 Pipe.confirmLowLevelRead(p, Pipe.sizeOf(p,msgIdx));
             Pipe.releaseReadLock(p);
	   	 }
	}

	protected void consumeStoreReplay(int index, Object target, 
			                            Pipe<PersistedBlobLoadConsumerSchema> p) {
		
		final int store = IntHashTable.getItem(serialStoreReplayPipeMap, NON_ZERO_BASE+p.id)-NON_ZERO_BASE;
		 
		SerialStoreReplayListener listener = (SerialStoreReplayListener)target;
		while (Pipe.hasContentToRead(p)) {                
	         
	   		 Pipe.markTail(p);			 
	         int msgIdx = Pipe.takeMsgIdx(p);
	         
	         if (msgIdx  == PersistedBlobLoadConsumerSchema.MSG_BEGINREPLAY_8) {
	        	 
	        	if (!listener.replayBegin(store)) {
            		 Pipe.resetTail(p);
            		 return;//continue later and repeat this same value.
	        	}
	        	 
	         } else if (msgIdx  == PersistedBlobLoadConsumerSchema.MSG_FINISHREPLAY_9) {
	        	 
	        	if (!listener.replayFinish(store)) {
            		 Pipe.resetTail(p);
            		 return;//continue later and repeat this same value.
	        	}
	        	 
	         } else if (msgIdx  == PersistedBlobLoadConsumerSchema.MSG_BLOCK_1) {
	        	 
	        	 long value = Pipe.takeLong(p);
	        	 
	        	 //byte array
	        	 DataInputBlobReader<PersistedBlobLoadConsumerSchema> stream = Pipe.openInputStream(p);
	        	 
	        	if (!listener.replay(store, value, stream)) {
            		 Pipe.resetTail(p);
            		 return;//continue later and repeat this same value.
	        	}   	 
	        	 
	        	 
	         } else {
	          	  logger.error("unrecognized message on {} ",p);
    	    	  throw new UnsupportedOperationException("unexpected message "+msgIdx);        	 
	         }
	         
   	      	 Pipe.confirmLowLevelRead(p, Pipe.sizeOf(p,msgIdx));
             Pipe.releaseReadLock(p);
	   	 }
	}

	/**
	 *
	 * @param builder BuilderImpl arg used with .shutdownRequested.get()
	 * @return builder.shutdownRequested.get()
	 */
	public static boolean isShutdownRequested(BuilderImpl builder) {
    	return builder.shutdownRequsted.get();
    }

	/**
	 *
	 * @param builder BuilderImpl arg used with shutdownIsComplete
	 * @return builder.shutdownIsComplete
	 */
	public static boolean isShutdownComplete(BuilderImpl builder) {
    	return builder.shutdownIsComplete;
    }

	/**
	 *
	 * @param builder BuilderImpl arg used with .lastcall and .shutdownRequested
	 * @param shutdownRunnable Runnable arg
	 */
	public static void requestSystemShutdown(BuilderImpl builder, Runnable shutdownRunnable) {
    	builder.lastCall = shutdownRunnable;
    	//Note: begin shutdown only when all the shutdown vetos are taken into account
    	//      setting this boolean triggers all the reactors to begin shutdown
    	builder.shutdownRequsted.set(true);
    	    	
    	//TODO: we should add a timeout, to force shutdown if some reactor does not respond.    	
    	    	
    }


	protected String toStringDetails = "\n";
    public String toString() {
    	return super.toString()+toStringDetails;
    }
    
    public final void setTimeEventSchedule(long rate, long start) {
        
        timeRate = rate;
        timeTrigger = start;

        timeEvents = (0 != timeRate) && (listener instanceof TimeListener);
    }
    
    
    protected ChildClassScannerVisitor visitAllStartups = new ChildClassScannerVisitor<StartupListenerTransducer>() {

		@Override
		public boolean visit(StartupListenerTransducer child, Object topParent, String name) {
			runStartupListener(child);
			return true;
		}

    	
    };
    
    private int consumeRequestMask;
    private int[] consumeRequestSequence;
    private long[] consumeRequestConnection;
    
    @Override
    public void startup() {              
    	
    	//////////////////////////////////////////////////////////////////
    	//ALL operators have been added to operators so it can be used to create consumers as needed
    	consumer = new ReactiveManagerPipeConsumer(listener, builder.operators, inputPipes);
    	    	
    	if (listener instanceof RestMethodListenerBase) {
    		
    		consumeRequestMask = (1<<builder.getHTTPServerConfig().getMaxConnectionBits())-1;
    		consumeRequestSequence = new int[1<<builder.getHTTPServerConfig().getMaxConnectionBits()];
    		consumeRequestConnection = new long[consumeRequestSequence.length];
    		if (!restRoutesDefined) {
    			throw new UnsupportedOperationException("a RestListener requires a call to includeRoutes() first to define which routes it consumes.");
    		}
    	}
    	
    	httpSpec = HTTPSpecification.defaultSpec();   	 
	    
        stageRate = (Number)GraphManager.getNota(graphManager, this.realStage.stageId,  GraphManager.SCHEDULE_RATE, null);
        
        timeProcessWindow = (null==stageRate? 0 : (int)(stageRate.longValue()/MS_to_NS));
         
        //does all the transducer startup listeners first
    	ChildClassScanner.visitUsedByClass( nameId, listener, 
    										visitAllStartups, 
    										StartupListenerTransducer.class);//populates outputPipes

    	//NOTE: the startup listener logic can not be called here since we are waiting on all other behaviors and stages
    	//      to also be started before we begin to do work. see top of run... that is where we call the startup listeners.
    	  	
    	
    }

	private void runStartupListener(StartupListenerBase startupListener) {
		long start = System.currentTimeMillis();
		startupListener.startup();
		long duration = System.currentTimeMillis()-start;

		if (duration > builder.getStartupLimitMS()) { //human perception
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
    	if (startupCompleted) {
	    	if (!shutdownInProgress) {    		
		    	if (isShutdownRequested(builder)) {	    		
		    		if (!shutdownCompleted) {
		    			beginShutdownIfNotVetoed();
		    			if (shutdownInProgress) {
		    				return;
		    			}	    			
		    		} else {
		    			assert(shutdownCompleted);
		    			assert(false) : "run should not have been called if this stage was shut down.";
		    			return;
		    		}
		    	}
		
		        if (timeEvents) {         					        	
		        	processTimeEvents(timeListener, timeTrigger);
		        	realStage.didWork();
		        }
		
		        if (null != tickListener) {
		        	tickListener.tickEvent();
		        	realStage.didWork();
		        }

		       	//all local behaviors
		       	ReactiveManagerPipeConsumer.process(consumer, this);
		      
		        
			   // int x = 1000;
			   // while (--x>=0) {
			    //each transducer
			    int j = consumers.size();
			    while(--j>=0) {
			    	ReactiveManagerPipeConsumer.process(consumers.get(j),this);
			    }
			  //  }
	
	    	} else {    		
	    		//shutdown in progress logic
	    		int i = outputPipes.length;    		
	    		while (--i>=0) {
	    			if ((null!=outputPipes[i]) && !Pipe.hasRoomForWrite(outputPipes[i], Pipe.EOF_SIZE)) {
	    				return;//must wait for pipe to empty
	    			}
	    		}		
	    		//now free to shut down, we know there is room to do so.
	    		this.realStage.requestShutdown();    		
	    		return;
	    	}
    	} else {    

    		//Do last so we complete all the initializations first
    		if (listener instanceof StartupListener) {
    			runStartupListener((StartupListenerBase)listener);
    			realStage.didWork();
    		}        
    		startupCompleted=true;
    	}
    }

	private void beginShutdownIfNotVetoed() {
		if (listener instanceof ShutdownListener) {    				
			if (((ShutdownListener)listener).acceptShutdown()) {
				int remaining = builder.liveShutdownListeners.decrementAndGet();
				assert(remaining>=0);
				shutdownInProgress = true;
			}
			//else continue with normal run processing	    				
		} else {
			//this one is not a listener so we must wait for all the listeners to close first	    				
			if (0 == builder.liveShutdownListeners.get()) {    					
				shutdownInProgress = true;
			}
			//else continue with normal run processing.	    				
		}
	}

	@Override    
    public void shutdown() {
		
		if  (!shutdownCompleted) {
			Pipe.publishEOF(outputPipes);	
			
			if (builder.totalLiveReactors.decrementAndGet()==0) {
				//ready for full system shutdown.
				if (null != builder.lastCall) {				
					new Thread(
							new Runnable() {							
								public void run() {
									builder.lastCall.run();
									builder.shutdownIsComplete = true;
								}
							}
							).start();
				} else {
					builder.shutdownIsComplete = true;
				}
			}
			shutdownCompleted = true;
		}
    }

    private void clearNextInSequence(long chnl, int seq) {
    	assert(consumeRequestConnection[(int)(chnl&consumeRequestMask)]==chnl) : "internal error";    	
    	consumeRequestSequence[(int)(chnl&consumeRequestMask)]=seq;
    }
    
	private boolean isNextInSequence(Pipe<HTTPRequestSchema> p) {

		if (Pipe.peekMsg(p, HTTPRequestSchema.MSG_RESTREQUEST_300)) {
			long chnl = Pipe.peekLong(p, HTTPRequestSchema.MSG_RESTREQUEST_300_FIELD_CHANNELID_21);
			int seq  = Pipe.peekInt(p, HTTPRequestSchema.MSG_RESTREQUEST_300_FIELD_SEQUENCE_26);
			
			int idxPos = (int)(chnl&consumeRequestMask);
			
			if (seq==0) {
				//starting fresh, wipe out old connection
				consumeRequestSequence[idxPos] = 0;
				consumeRequestConnection[idxPos] = chnl;
			} else {
				if (consumeRequestConnection[idxPos] == chnl) {
					//connection is still live is the the right index
					return (consumeRequestSequence[idxPos]==(seq-1));
				} else {
					//this is an old discontinued connection so we should drop it
					Pipe.skipNextFragment(p);
					return Pipe.hasContentToRead(p);					
				}
			}
		}
		return true;
	}
	
	
	
    final void consumeRestRequest(Object listener, Pipe<HTTPRequestSchema> p) {
		
    	  while (Pipe.hasContentToRead(p) && isNextInSequence(p)) {                
             
    		  Pipe.markTail(p);             
              int msgIdx = Pipe.takeMsgIdx(p);
    	  
    	      if (HTTPRequestSchema.MSG_RESTREQUEST_300==msgIdx) {
    	    	 
    	    	  long connectionId = Pipe.takeLong(p);
    	    	  final int sequenceNo = Pipe.takeInt(p);    	    	  

    	    	  int routeVerb = Pipe.takeInt(p);
    	    	  int routeId = routeVerb>>>HTTPVerb.BITS;
    	    	  int verbId = HTTPVerb.MASK & routeVerb;
    	    	      	    	      	    	  
    	    	  HTTPRequestReader reader = (HTTPRequestReader)Pipe.openInputStream(p);
   	  				  
    	    	  int parallelRevision = Pipe.takeInt(p);
    	    	  int parallelIdx = parallelRevision >>> HTTPRevision.BITS;
    	    	  int revision = HTTPRevision.MASK & parallelRevision;
    	    	  
				  reader.setRevisionId(revision);
    	    	  int context = Pipe.takeInt(p);   	    	  
    	    	  
    	    	  reader.setRouteId(routeId, graphManager.recordTypeData.structAssociatedObject(DataInputBlobReader.getStructType(reader)));
    	    	      	    	  
    	    	  assert(parallelIdx<OrderSupervisorStage.CLOSE_CONNECTION_MASK);
    	    	  
    	    	  //if (0!=(OrderSupervisorStage.CLOSE_CONNECTION_MASK&context)) {
    	    	  //	  logger.info("\nclosed discovered {} sent from client.. ",connectionId);
    	    	  //}
    	    	  
    	    	  //all these values are required in order to ensure the right sequence order once processed.
    	    	  long sequenceCode = (((long)(parallelIdx|(OrderSupervisorStage.CLOSE_CONNECTION_MASK&context))  )<<32) | ((long)sequenceNo);
    	    	  
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
    	    	  clearNextInSequence(connectionId, sequenceNo);
    	      } else {
    	    	  logger.error("unrecognized message on {} ",p);
    	    	  throw new UnsupportedOperationException("unexpected message "+msgIdx);
    	      }
        
    	      Pipe.confirmLowLevelRead(p, Pipe.sizeOf(p,msgIdx));
              Pipe.releaseReadLock(p);
              
    	  }
    	   	
    	
	}


	final void consumeNetResponse(Object listener, Pipe<NetResponseSchema> pipeIn) {
		
    	 while (Pipe.hasContentToRead(pipeIn)) {                
             
       		 Pipe.markTail(pipeIn);
    	
             final int msgIdx = Pipe.takeMsgIdx(pipeIn);
             
            // logger.info("\nresponse from HTTP request. Type is {} ",msgIdx);
             
             switch (msgIdx) {
	             case NetResponseSchema.MSG_RESPONSE_101:

	            	 long ccId1 = Pipe.takeLong(pipeIn);
	            	 int clientSessionId = Pipe.takeInt(pipeIn);
	            	 int flags = Pipe.takeInt(pipeIn);
	            	 
	          
	            	 assert(0!= (Pipe.STRUCTURED_POS_MASK & Pipe.peekInt(pipeIn,0))) : "must be structured response";
	            	 assert((Pipe.blobMask(pipeIn) & Pipe.peekInt(pipeIn,0))==0) :"bad meta for first field, found idx: "+(Pipe.blobMask(pipeIn) & Pipe.peekInt(pipeIn,0));
	            	 
	            	 
	            	 //NOTE: this HTTPResponseReader object will show up n times in a row until
	            	 //      the full file is complete.  No files will be interleaved.
            		 HTTPResponseReader reader = (HTTPResponseReader)Pipe.openInputStream(pipeIn);
	                
            		 
	            	 //logger.trace("running position {} ",reader.absolutePosition());
	            	 if (reader.available()>=2) {
					     reader.setStatusCode(reader.readShort());
					     reader.setConnectionId(ccId1);
					     reader.setClientSessionId(clientSessionId);
		            	 reader.setFlags(flags);
		 
		            	 if (!((HTTPResponseListener)listener).responseHTTP(reader)) {
		            		 Pipe.resetTail(pipeIn);
		            		 //logger.info("CONTINUE LATER");
		            		 return;//continue later and repeat this same value.
		            	 }
	            	 } else {
	            		 //if we did not get the status then this was closed mid stream and must not respond
	            	 }
	            	 
	            	 break;
	             case NetResponseSchema.MSG_CONTINUATION_102:
	            	 long fieldConnectionId = Pipe.takeLong(pipeIn);
	            	 int fieldClientSessionId = Pipe.takeInt(pipeIn);
	            	 int flags2 = Pipe.takeInt(pipeIn);
	            	 
            		 HTTPResponseReader continuation = (HTTPResponseReader)Pipe.openInputStream(pipeIn);
            		 
            		 continuation.setFlags(flags2);
            		 continuation.setConnectionId(fieldConnectionId);
            		 continuation.setClientSessionId(fieldClientSessionId);
            		 //logger.trace("continuation with "+Integer.toHexString(flags2)+" avail "+continuation.available());
            		 
	            	 if (!((HTTPResponseListener)listener).responseHTTP(continuation)) {
						 Pipe.resetTail(pipeIn);
						 return;//continue later and repeat this same value.
					 }
            		 
	            	 break;
	             case NetResponseSchema.MSG_CLOSED_10:

	            	 long connectionId = Pipe.takeLong(pipeIn);
	            	 int sessionId = Pipe.takeInt(pipeIn);

	            	 HTTPResponseReader hostReader = (HTTPResponseReader)Pipe.openInputStream(pipeIn);
	            	 	            	 
	            	 int port = Pipe.takeInt(pipeIn);//the caller does not care which port we were on.
	            	 	            	 
	            	 hostReader.setFlags(ServerCoordinator.END_RESPONSE_MASK | 
	            			             ServerCoordinator.CLOSE_CONNECTION_MASK);
	            	 hostReader.setConnectionId(connectionId);
	            	 hostReader.setClientSessionId(sessionId);
					
	            	 if (!((HTTPResponseListener)listener).responseHTTP(hostReader)) {
	            		 Pipe.resetTail(pipeIn);
	            		 return;//continue later and repeat this same value.
	            	 }	            	 
	            	 
	            	 break;
	             case -1:
	            	 //shutdown request, just consume at this point.	            	 
	            	 break;
	             default:
	                 throw new UnsupportedOperationException("Unknown id: "+msgIdx+"  "+pipeIn);
             }
            
             Pipe.confirmLowLevelRead(pipeIn, Pipe.sizeOf(NetResponseSchema.instance, msgIdx));
             Pipe.releaseReadLock(pipeIn);
             
             
    	 }
    			
    	
	}

	final void consumePrivateMessage(int index, Object listener, Pipe<MessagePrivate> p) {

		final PrivateTopic topic = receivePrivateTopics[index];
		assert (null!=topic);
		assert (null!=listener);
		
		while (Pipe.hasContentToRead(p)) {
					
				Pipe.markTail(p);
				
	            final int msgIdx = Pipe.takeMsgIdx(p);             		            
	            
	            if (-1 == msgIdx) {
	            	Pipe.confirmLowLevelRead(p, Pipe.EOF_SIZE);
	            	Pipe.releaseReadLock(p);
	            	this.realStage.requestShutdown();
	            	return;
	            }
	            assert(MessagePrivate.MSG_PUBLISH_1 == msgIdx) : "message id "+msgIdx;
	            
	            
	            int dispatch = -1; //TODO: move all these topic lookups to be done once and stored under index..
	            
	            try {
	                if (((null==methodReader) 
	                	|| ((dispatch=customDispatchTargetForPrivateTopic(topic))<0))
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
                } catch (Throwable t) {
                	logger.warn("Unexpected error ",t); //TODO: refine this
                	             	
                	Pipe.resetTail(p);
            		return;//continue later and repeat this same value.
                }
	            
	            Pipe.confirmLowLevelRead(p, SIZE_OF_PRIVATE_MSG_PUB);
	            Pipe.releaseReadLock(p);
		}
		
	}

	private int customDispatchTargetForPrivateTopic(final PrivateTopic topic) {
		//the dispatch function can never be changed at runtime so no need to look it up again
		//once it is known.
		if (-2 == topic.customDispatchId) {
			//-1 represents that there is no custom distpatch but we did look it up once.
			topic.customDispatchId = (int)TrieParserReader.query(methodReader, methodLookup, topic.topic);			
		}
		return topic.customDispatchId;	
	}
	
	final void consumePubSubMessage(Object listener, Pipe<MessageSubscription> p) {

		while (
				Pipe.hasContentToRead(p) &&
				Pipe.peekMsg(p, 
						//all the switch states are listed here
						MessageSubscription.MSG_STATECHANGED_71, 
						MessageSubscription.MSG_PUBLISH_103, 
						-1)				
				) {			
			
			Pipe.markTail(p);
			
            final int msgIdx = Pipe.takeMsgIdx(p);             		            
            
            switch (msgIdx) {
                case MessageSubscription.MSG_PUBLISH_103:
                    	
                    	final int meta = Pipe.takeByteArrayLength(p);
                    	final int len = Pipe.takeByteArrayMetaData(p);                    	
                    	final int pos = Pipe.convertToPosition(meta, p);
                    	                    	               	
                    	mutableTopic.setToField(p, meta, len);
	  
	                    DataInputBlobReader<MessageSubscription> reader = Pipe.inputStream(p);
	                    reader.openLowLevelAPIField();
	                    	                           
	                    int dispatch = -1;
	                    
	                    try {
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
	                    } catch (Throwable t) {
	                    	logger.warn("Unexpected error ",t); //TODO: refine this
	                    	             	
	                    	Pipe.resetTail(p);
                    		return;//continue later and repeat this same value.
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
                	beginShutdownIfNotVetoed();
                	
                    Pipe.confirmLowLevelRead(p, Pipe.EOF_SIZE);
                    Pipe.releaseReadLock(p);
                    return;
            }
            Pipe.releaseReadLock(p);
        }
    }


	private final int methodLookup(Pipe<MessageSubscription> p, final int len, final int pos) {
		int result = (int)TrieParserReader.query(methodReader, methodLookup,
				Pipe.blob(p), pos, len, Pipe.blobMask(p));
		assert(result!=-1) : "requested method was not found in: "+methodReader.debugAsUTF8(methodReader, new StringBuilder());
		return result;
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
			
			if (0==builder.routerConfig().totalRoutesCount()) {
				builder.defineRoute().path("${path}").routeId();
			}
			
			int[] routes = new int[builder.routerConfig().totalRoutesCount()];
			int i = routes.length;
			while (--i>=0) {
				routes[i]=i;
			}
			includeRoutes(routes);
			return this;
		} else {
			throw new UnsupportedOperationException("The Listener must be an instance of "+RestListener.class.getSimpleName()+" in order to call this method.");
		}
	}
	

	
	
	@Override
	public final ListenerFilter includeRoutes(int... routeIds) {

		if (listener instanceof RestListener) {	
			addRoutesToPipeMapping(routeIds);
			return this;
		} else {
			throw new UnsupportedOperationException("The Listener must be an instance of "+RestListener.class.getSimpleName()+" in order to call this method.");
		}
	}

	
	private int pipeIndexForNonJSONRequests = -1;
	
	private void addRoutesToPipeMapping(int ... routeIds) {

		final PipeConfig<HTTPRequestSchema> pipeConfig = builder.pcm.getConfig(HTTPRequestSchema.class);

		final int track = parallelInstance==-1 ? 0 : parallelInstance;
		int i = routeIds.length;
		while (--i>=0) {	
			
			if (null==builder.routerConfig().JSONExtractor(routeIds[i])) {
				//this route does not use JSON so we can join them together
				if (-1 == pipeIndexForNonJSONRequests) {//the one ring to rule them all was not yet forged.					
					pipeIndexForNonJSONRequests = inputPipes.length;
					inputPipes = PronghornStage.join(inputPipes, builder.newHTTPRequestPipe(pipeConfig));
				}
				
				restRoutesDefined |= builder.appendPipeMappingIncludingGroupIds(
						       (Pipe<HTTPRequestSchema>) inputPipes[pipeIndexForNonJSONRequests], track, routeIds);
			
			} else {
				//every JSON request needs its own ring.
				Pipe<HTTPRequestSchema> jsonPipe = builder.newHTTPRequestPipe(pipeConfig);
				inputPipes = PronghornStage.join(inputPipes, jsonPipe);
				restRoutesDefined |= builder.appendPipeMappingIncludingGroupIds(jsonPipe, track, routeIds);								
			}
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
				  
					int p = parallelInstance==-1?count:parallelInstance;
					restRoutesDefined |= builder.appendPipeMappingExcludingGroupIds(
							                 (Pipe<HTTPRequestSchema>) inputPipes[i], 
																p, routeIds);
								
					count++;
				}
			}
			return this;
		} else {
			throw new UnsupportedOperationException("The Listener must be an instance of "+RestListener.class.getSimpleName()+" in order to call this method.");
		}
	}

	/**
	 *
	 * @param topic CharSequence arg used to specify subscription to add
	 * @param callable <code>final</code> CallableMethod arg used to assert(childIsFoundIn)
	 * @return callable.method(title, reader)
	 */
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
	
	private static final int NON_ZERO_BASE = 111;
	private IntHashTable serialStoreProdAckPipeMap; //TODO: could use perfect hash in the future.
	private IntHashTable serialStoreRelAckPipeMap;  //TODO: could use perfect hash in the future.
	private IntHashTable serialStoreReplayPipeMap;  //TODO: could use perfect hash in the future.

	public final ListenerFilter includeSerialStoreWriteAck(int ... id) {		
		serialStoreProdAckPipeMap = new IntHashTable(IntHashTable.computeBits(id.length*3));		
		int i = id.length;
		while (--i>=0) {
			Pipe<PersistedBlobLoadProducerSchema> pipe = builder.serialStoreWriteAck[i];
			if (pipe==null) {
				 throw new UnsupportedOperationException("The id "+id+" write ack has already been assined to another behavior.\n Only 1 behavior may consume this message");
			} else {
				 IntHashTable.setItem(serialStoreProdAckPipeMap, NON_ZERO_BASE+pipe.id, NON_ZERO_BASE+i);
				 builder.serialStoreWriteAck[i] = null;
			}			
		}	
		return this;
	}

	public final ListenerFilter includeSerialStoreReleaseAck(int ... id) {
		serialStoreRelAckPipeMap = new IntHashTable(IntHashTable.computeBits(id.length*3));	
		int i = id.length;
		while (--i>=0) {
			Pipe<PersistedBlobLoadReleaseSchema> pipe = builder.serialStoreReleaseAck[i];
			if (pipe==null) {
				 throw new UnsupportedOperationException("The id "+id+" release ack has already been assined to another behavior.\n Only 1 behavior may consume this message");
			} else {
				 IntHashTable.setItem(serialStoreRelAckPipeMap, NON_ZERO_BASE+pipe.id, NON_ZERO_BASE+i);
				 builder.serialStoreReleaseAck[i] = null;
			}			
		}	
		return this;
	}
	
	public final ListenerFilter includeSerialStoreReplay(int ... id) {
		serialStoreReplayPipeMap = new IntHashTable(IntHashTable.computeBits(id.length*3));		
		int i = id.length;
		while (--i>=0) {
			Pipe<PersistedBlobLoadConsumerSchema> pipe = builder.serialStoreReplay[i];
			if (pipe==null) {
				 throw new UnsupportedOperationException("The id "+id+" replay has already been assined to another behavior.\n Only 1 behavior may consume this message");
			} else {
				 IntHashTable.setItem(serialStoreReplayPipeMap, NON_ZERO_BASE+pipe.id, NON_ZERO_BASE+i);
				 builder.serialStoreReplay[i] = null;
			}
		}
		return this;
	}	
	
	public ListenerFilter includeRoutesByAssoc(Object ... assocRouteObjects) {
		int r = assocRouteObjects.length;
		int[] routeIds = new int[r];
		while (--r >= 0) {
			routeIds[r] = builder.routerConfig().lookupRouteIdByIdentity(assocRouteObjects[r]);

		}		
		includeRoutes(routeIds);
		return this;
	}
	
	public ListenerFilter includeRoutesByAssoc(Object assocRouteObject, final CallableRestRequestReader callable) {
		return includeRouteById(builder.routerConfig().lookupRouteIdByIdentity(assocRouteObject), callable);		
	}
	
	public <T extends Behavior> ListenerFilter includeRoutesByAssoc(Object assocRouteObject, final CallableStaticRestRequestReader<T> callable) {
		return includeRouteById(builder.routerConfig().lookupRouteIdByIdentity(assocRouteObject), callable);
	}
	
	public <E extends Enum<E>>ListenerFilter includeRoutes(E ... assocRouteObjects) {
		int r = assocRouteObjects.length;
		int[] routeIds = new int[r];
		while (--r >= 0) {
			routeIds[r] = builder.routerConfig().lookupRouteIdByIdentity(assocRouteObjects[r]);
		}		
		includeRoutes(routeIds);
		return this;
	}
	public <E extends Enum<E>>ListenerFilter includeRoutes(E assocRouteObject, final CallableRestRequestReader callable) {
		return includeRouteById(builder.routerConfig().lookupRouteIdByIdentity(assocRouteObject), callable);
	}
	public <T extends Behavior, E extends Enum<E> > ListenerFilter includeRoutes(E assocRouteObject, final CallableStaticRestRequestReader<T> callable) {
		return includeRouteById(builder.routerConfig().lookupRouteIdByIdentity(assocRouteObject), callable);
	}
	
	public final ListenerFilter includeRouteById(int routeId, final CallableRestRequestReader callable) {
		
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
		addRoutesToPipeMapping(routeId);
		return this;
	}

	/**
	 *
	 * @param routeId int arg to set route it used to compare to restRequestReader.length
	 * @param callable
	 * @param <T>
	 * @return listener filter
	 */
	public final <T extends Behavior> ListenerFilter includeRouteById(int routeId, final CallableStaticRestRequestReader<T> callable) {
		
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
		addRoutesToPipeMapping(routeId);
		return this;
	}

	/**
	 *
	 * @return builder.behaviorId((Behavior)listener)
	 */
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
		
		String scopedTopic = BuilderImpl.buildTrackTopic(topic, BuilderImpl.trackNameBuilder(parallelInstance));

		assert(null!=this.behaviorName);
		builder.possiblePrivateTopicConsumer(this, topic, parallelInstance);
		totalSupscriptions++;
		
		if (null == methods) {
			methodLookup = new TrieParser(16,1,false,false,false);
			methodReader = new TrieParserReader(true);
			methods = new CallableStaticMethod[0];
		}
		
		if (!startupCompleted && listener instanceof PubSubMethodListenerBase) {
			builder.addStartupSubscription(topic, System.identityHashCode(listener), parallelInstance);
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
	
			totalSupscriptions++;
			String scopedTopic = BuilderImpl.buildTrackTopic(topic, BuilderImpl.trackNameBuilder(parallelInstance));
			
			assert(null!=this.behaviorName);
			builder.possiblePrivateTopicConsumer(this, topic, parallelInstance);
						
			builder.addStartupSubscription(topic, System.identityHashCode(listener), parallelInstance);		
			
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
   
	
	public int getFeatures(Pipe<TrafficOrderSchema> pipe) {
		//logger.info("getFeatuers was called, should visit all command channels");
		ccmwp.init(pipe);
		ChildClassScanner.visitUsedByClass(nameId, listener, ccmwp, MsgCommandChannel.class);		
		return ccmwp.features();
	}

	public void regPrivateTopics() {
		//logger.info("regPrivateTopics was called, should visit all command channels");
		ChildClassScanner.visitUsedByClass(nameId, listener, new PrivateTopicReg(this), MsgCommandChannel.class);		
	}
	
	@Override
	public <E extends Enum<E>> ListenerFilter acceptHostResponses(ClientHostPortInstance... httpSessions) {

		int behaviorId = builder.behaviorId(listener);		
		int j = httpSessions.length;
		while(--j >= 0) {
			
			//NOTE: this is not a good implementation and should be revisited at some point.
			//     we could store the pipeId instead then use the graph to look them up directly.
			int pipeIdx = builder.netResponsePipeIdxCounter++; //depends on graph returning the same order.
			
			
			//register listener will set these values before we use include
		    //we added one more uniqueId to the same pipeIdx given this listeners id
			ClientHostPortInstance.setTargetResponsePipeIdx(httpSessions[j], pipeIdx);
			
		   // logger.info("\nregister session {} with pipe {}",httpSessions[j].sessionId,pipeIdx);
		    
			Pipe<NetResponseSchema> buildNetResponsePipe = builder.buildNetResponsePipe();
			
		    JSONExtractorCompleted ex = httpSessions[j].jsonExtractor();
		    if (null != ex) {
		    	
		    	//add this JSON extraction to the struct associated with this session
				ex.addToStruct(builder.gm.recordTypeData, 
						       ClientCoordinator.structureId(httpSessions[j].sessionId, builder.gm.recordTypeData));		    	
								
				
		    	Pipe<NetResponseSchema> secondPipe = builder.buildNetResponsePipe();		    			    	
		    	new NetResponseJSONExtractionStage(builder.gm, ex, buildNetResponsePipe, secondPipe);
		    	//the output pipe here goes into the inputs for our stage
		    	buildNetResponsePipe = secondPipe;		    	
		
		    	
		    }
		    
		    //These are inputs to this reactive lister
		    inputPipes = PronghornStage.join(inputPipes, buildNetResponsePipe);
		    
		}
		
		return this;
	}

	public String behaviorName() {
		return nameId;
	}

	public void addInputPronghornPipes(Pipe ... pipes) {
		inputPipes = PronghornStage.join(inputPipes, pipes);
	}
	
	public void addOutputPronghornPipes(Pipe ... pipes) {
		outputPipes = PronghornStage.join(outputPipes, pipes);
	}
    
}
