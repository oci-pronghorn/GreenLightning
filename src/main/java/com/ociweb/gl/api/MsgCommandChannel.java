package com.ociweb.gl.api;

import com.ociweb.gl.impl.BuilderImpl;
import com.ociweb.gl.impl.file.SerialStoreConsumer;
import com.ociweb.gl.impl.file.SerialStoreProducer;
import com.ociweb.gl.impl.schema.MessagePrivate;
import com.ociweb.gl.impl.schema.MessagePubSub;
import com.ociweb.gl.impl.schema.TrafficOrderSchema;
import com.ociweb.gl.impl.stage.PublishPrivateTopics;
import com.ociweb.pronghorn.network.schema.ClientHTTPRequestSchema;
import com.ociweb.pronghorn.network.schema.ServerResponseSchema;
import com.ociweb.pronghorn.pipe.*;
import com.ociweb.pronghorn.stage.file.schema.PersistedBlobStoreConsumerSchema;
import com.ociweb.pronghorn.stage.file.schema.PersistedBlobStoreProducerSchema;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;
import com.ociweb.pronghorn.util.BloomFilter;
import com.ociweb.pronghorn.util.CharSequenceToUTF8Local;
import com.ociweb.pronghorn.util.TrieParserReader;
import com.ociweb.pronghorn.util.TrieParserReaderLocal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Represents a dedicated channel for communicating with a single device
 * or resource on an IoT system.
 */
public class MsgCommandChannel<B extends BuilderImpl> {

	final TrieParserReader READER = new TrieParserReader(true);

	private final static Logger logger = LoggerFactory.getLogger(MsgCommandChannel.class);
	
	private boolean isInit = false;
    Pipe<TrafficOrderSchema> goPipe;
    
    ///////////////////////////All the known data pipes
    Pipe<MessagePubSub> messagePubSub;
    Pipe<ClientHTTPRequestSchema> httpRequest;
    Pipe<ServerResponseSchema>[] netResponse;
	private Pipe<PersistedBlobStoreProducerSchema>[] serialStoreProdPipes;
	private Pipe<PersistedBlobStoreConsumerSchema>[] serialStoreConsPipes;
	
	private final byte[] track;
    
    private Pipe<MessagePubSub>[] exclusivePubSub;

	static final byte[] RETURN_NEWLINE = "\r\n".getBytes();
       
    int lastResponseWriterFinished = 1;//starting in the "end" state
    
    private String[] exclusiveTopics =  new String[0];
    
    protected AtomicBoolean aBool = new AtomicBoolean(false);   

    protected static final long MS_TO_NS = 1_000_000;
         
    Behavior listener;
    
    //TODO: add GreenService class for getting API specific objects.
    public static final int DYNAMIC_MESSAGING = 1<<0;
    public static final int STATE_MACHINE = DYNAMIC_MESSAGING;//state machine is based on DYNAMIC_MESSAGING;    
   
    public static final int NET_REQUESTER      = 1<<1;//HTTP client requests
    public static final int NET_RESPONDER      = 1<<2;//HTTP server responder
    public static final int USE_DELAY          = 1<<3;//support for delay between commands
    public static final int USE_SERIAL_STORE   = 1<<4;//support to store identified ChannelWriter blocks
    
    public static final int ALL = DYNAMIC_MESSAGING | NET_REQUESTER | NET_RESPONDER | USE_SERIAL_STORE;
      
    public final B builder;

	public int maxHTTPContentLength;
	
	protected Pipe<?>[] optionalOutputPipes;
	public int initFeatures; //this can be modified up to the moment that we build the pipes.

	PublishPrivateTopics publishPrivateTopics;	

	protected PipeConfigManager pcm;
	private final int parallelInstanceId;

    public MsgCommandChannel(GraphManager gm, B hardware,
				  		    int parallelInstanceId,
				  		    PipeConfigManager pcm
				           ) {
    	this(gm,hardware,ALL, parallelInstanceId, pcm);
    }
    
    public MsgCommandChannel(GraphManager gm, B builder,
    					  int features,
    					  int parallelInstanceId,
    					  PipeConfigManager pcm
    		             ) {

       this.initFeatures = features;//this is held so we can check at every method call that its configured right
       this.builder = builder;
       this.pcm = pcm;
       this.parallelInstanceId = parallelInstanceId;
              
       this.track = parallelInstanceId<0 ? null : trackNameBuilder(parallelInstanceId);
    }

    ////////////////////////////////////
    //new method API
    ////////////////////////////////////

	/**
	 *
	 * @param id int id to be passed to builder.serialStoreWrite
	 * @return SerialStoreProducer(mypipe)
	 */
	public SerialStoreProducer newSerialStoreProducer(int id) {
	  	if (isInit) {
    		throw new UnsupportedOperationException("Too late, ensureHTTPClientRequesting method must be called in define behavior.");
    	}
    	this.initFeatures |= USE_SERIAL_STORE;	
    	
    	Pipe<PersistedBlobStoreProducerSchema> myPipe = builder.serialStoreWrite[id];
    	if (null==myPipe) {
    		throw new UnsupportedOperationException("only 1 command channel can write into this store "+id);    		
    	} 
    	builder.serialStoreWrite[id]=null;    	
    	serialStoreProdPipes = growP(serialStoreProdPipes, myPipe);    	
    	return new SerialStoreProducer(myPipe);
	}

	/**
	 *
	 * @param id int id be passed to builder.serialStoreRequestReplay
	 * @return SerialStoreProducer(mypipe)
	 */
	public SerialStoreConsumer newSerialStoreConsumer(int id) {
	  	if (isInit) {
    		throw new UnsupportedOperationException("Too late, ensureHTTPClientRequesting method must be called in define behavior.");
    	}
    	this.initFeatures |= USE_SERIAL_STORE;
    	
    	Pipe<PersistedBlobStoreConsumerSchema> myPipe = builder.serialStoreRequestReplay[id];
    	if (null==myPipe) {
    		throw new UnsupportedOperationException("only 1 command channel can request replay for this store "+id);    		
    	}
    	builder.serialStoreRequestReplay[id] = null;
    	serialStoreConsPipes = growC(serialStoreConsPipes,myPipe);    	
    	return new SerialStoreConsumer(myPipe);
	}

	/**
	 *
	 * @return PubSubService(this)
	 */
	public PubSubService newPubSubService() {
		return new PubSubService(this);
	}

	/**
	 *
	 * @param cmd MsgCommandChannel arg
	 * @return cmd.pcm
	 */
	public static PipeConfigManager PCM(MsgCommandChannel cmd) {
		return cmd.pcm;
	}

	/**
	 *
	 * @param queueLength int to be passed to PubSubService
	 * @param maxMessageSize int to be passed to PubSubService
	 * @return PubSubService(this, queueLength, maxMessageSize)
	 */
	public PubSubService newPubSubService(int queueLength, int maxMessageSize) {
		return new PubSubService(this,queueLength,maxMessageSize);
	}

	/**
	 *
	 * @return HTTPRequestService(this)
	 */
	public HTTPRequestService newHTTPClientService() {
		return new HTTPRequestService(this);
	}

	/**
	 *
	 * @param queueLength int arg to be passed to HTTPRequestService
	 * @param maxMessageSize int arg to be passed to HTTPRequestService
	 * @return HTTPRequestService(this, queueLength, maxMessageSize)
	 */
	public HTTPRequestService newHTTPClientService(int queueLength, int maxMessageSize) {
		return new HTTPRequestService(this,queueLength,maxMessageSize);
	}

	/**
	 *
	 * @return HTTPResponseService
	 */
	public HTTPResponseService newHTTPResponseService() {
		return new HTTPResponseService(this);
	}

	/**
	 *
	 * @param queueLength int arg to be passed to HTTPResponseService
	 * @param maxMessageSize int arg to be passed to HTTPResponseService
	 * @return HTTPResponseService(this, queueLength, maxMessageSize)
	 */
	public HTTPResponseService newHTTPResponseService(int queueLength, int maxMessageSize) {
		return new HTTPResponseService(this,queueLength,maxMessageSize);
	}

	/**
	 *
	 * @return new DelayService(this)
	 */
	public DelayService newDelayService() {
		return new DelayService(this);
	}

    ////////////////////////////////////
    ////////////////////////////////////
    
    
    //common method for building topic suffix
	static byte[] trackNameBuilder(int parallelInstanceId) {		
		return CharSequenceToUTF8Local.get()
		.append("/")
		.append(Integer.toString(parallelInstanceId)).asBytes();
	}

//	@Deprecated
//    public static boolean hasRoomFor(MsgCommandChannel<?> cmd, int messageCount) {
//		return null==cmd.goPipe || Pipe.hasRoomForWrite(cmd.goPipe, 
//    			FieldReferenceOffsetManager.maxFragmentSize(Pipe.from(cmd.goPipe))*messageCount);
//    }
//    
//    @Deprecated
//    public static boolean hasRoomForHTTP(MsgCommandChannel<?> cmd, int messageCount) {
//    	assert(cmd.httpRequest!=null) : "Client side HTTP Request must be enabled";    	
//		return Pipe.hasRoomForWrite(cmd.httpRequest, 
//    			FieldReferenceOffsetManager.maxFragmentSize(
//    					Pipe.from(cmd.httpRequest))*messageCount);
//    }
//    
//    @Deprecated
//    public void ensureDynamicMessaging() {
//    	if (isInit) {
//    		throw new UnsupportedOperationException("Too late, ensureDynamicMessaging method must be called in define behavior.");
//    	}
//    	this.initFeatures |= DYNAMIC_MESSAGING;
//    }
//    
//    @Deprecated
//    public void ensureDynamicMessaging(int queueLength, int maxMessageSize) {
//    	if (isInit) {
//    		throw new UnsupportedOperationException("Too late, ensureDynamicMessaging method must be called in define behavior.");
//    	}
//    	growCommandCountRoom(queueLength);
//    	this.initFeatures |= DYNAMIC_MESSAGING;  
//    	
//    	pcm.ensureSize(MessagePubSub.class, queueLength, maxMessageSize);
//
//		//also ensure consumers have pipes which can consume this.    		
//		pcm.ensureSize(MessageSubscription.class, queueLength, maxMessageSize);
//		
//		//IngressMessages Confirm that MQTT ingress is big enough as well			
//		pcm.ensureSize(IngressMessages.class, queueLength, maxMessageSize);
//	
//    }

	public static boolean isTooSmall(int queueLength, int maxMessageSize, PipeConfig<?> config) {
		return queueLength>config.minimumFragmentsOnPipe() || maxMessageSize>config.maxVarLenSize();
	}

	
	private Pipe<PersistedBlobStoreProducerSchema>[] growP(Pipe<PersistedBlobStoreProducerSchema>[] source,
													      Pipe<PersistedBlobStoreProducerSchema> myPipe) {
		Pipe[] result;
		if (null==source) {
			result = new Pipe[1];	
		} else {
			result = new Pipe[source.length+1];		
			System.arraycopy(source, 0, result, 0, source.length);
		}
		result[result.length-1]=myPipe;		
		return (Pipe<PersistedBlobStoreProducerSchema>[])result;
	}


			
    private Pipe<PersistedBlobStoreConsumerSchema>[] growC(Pipe<PersistedBlobStoreConsumerSchema>[] source,
													     Pipe<PersistedBlobStoreConsumerSchema> myPipe) {
		Pipe[] result;
		if (null==source) {
			result = new Pipe[1];	
		} else {
			result = new Pipe[source.length+1];		
			System.arraycopy(source, 0, result, 0, source.length);
		}
		result[result.length-1]=myPipe;		
		return (Pipe<PersistedBlobStoreConsumerSchema>[])result;
	}

//    @Deprecated
//	public void ensureHTTPClientRequesting() {
//    	if (isInit) {
//    		throw new UnsupportedOperationException("Too late, ensureHTTPClientRequesting method must be called in define behavior.");
//    	}
//    	this.initFeatures |= NET_REQUESTER;
//    }
//    
//    @Deprecated
//    public void ensureHTTPClientRequesting(int queueLength, int maxMessageSize) {
//    	if (isInit) {
//    		throw new UnsupportedOperationException("Too late, ensureHTTPClientRequesting method must be called in define behavior.");
//    	}
//    	growCommandCountRoom(queueLength);
//    	this.initFeatures |= NET_REQUESTER;
//    	
//    	pcm.ensureSize(ClientHTTPRequestSchema.class, queueLength, maxMessageSize);
//
//    }
//
//    @Deprecated
//    public void ensureDelaySupport() {
//    	if (isInit) {
//    		throw new UnsupportedOperationException("Too late, ensureDelaySupport method must be called in define behavior.");
//    	}
//    	this.initFeatures |= USE_DELAY;
//    }
//    
//    @Deprecated
//    public void ensureHTTPServerResponse() {
//    	if (isInit) {
//    		throw new UnsupportedOperationException("Too late, ensureHTTPServerResponse method must be called in define behavior.");
//    	}
//    	this.initFeatures |= NET_RESPONDER;
//    }
//    
//    @Deprecated
//    public void ensureHTTPServerResponse(int queueLength, int maxMessageSize) {
//    	if (isInit) {
//    		throw new UnsupportedOperationException("Too late, ensureHTTPServerResponse method must be called in define behavior.");
//    	}
//    	growCommandCountRoom(queueLength);
//    	this.initFeatures |= NET_RESPONDER;    	
//    	
//    	pcm.ensureSize(ServerResponseSchema.class, queueLength, maxMessageSize);
//
//    }

	public static void growCommandCountRoom(MsgCommandChannel<?> cmd, int count) {
		if (cmd.isInit) {
    		throw new UnsupportedOperationException("Too late, growCommandCountRoom method must be called in define behavior.");
    	}
    	
    	PipeConfig<TrafficOrderSchema> goConfig = cmd.pcm.getConfig(TrafficOrderSchema.class);
    	cmd.pcm.addConfig(count + goConfig.minimumFragmentsOnPipe(), 0, TrafficOrderSchema.class);
	}
    
    
	@SuppressWarnings("unchecked")
	private void buildAllPipes() {
		   
		   if (!isInit) {
			   
			   isInit = true;
			   this.messagePubSub = ((this.initFeatures & DYNAMIC_MESSAGING) == 0) ? null : newPubSubPipe(pcm.getConfig(MessagePubSub.class), builder);
			   this.httpRequest   = ((this.initFeatures & NET_REQUESTER) == 0)     ? null : newNetRequestPipe(pcm.getConfig(ClientHTTPRequestSchema.class), builder);
			   
			   //when looking at features that requires cops, eg go pipes we ignore
			   //the following since they do not use cops but are features;
			   int filteredFeatures = this.initFeatures;
			   if (0!=(NET_RESPONDER&filteredFeatures)) {
				   filteredFeatures ^= NET_RESPONDER;
			   }  
			   
			   int featuresCount = Integer.bitCount(filteredFeatures);
			   if (featuresCount>1 || featuresCount==USE_DELAY) {
				   this.goPipe = newGoPipe(pcm.getConfig(TrafficOrderSchema.class));
			   } else {
				   assert(null==goPipe);
			   }
			   
			   /////////////////////////
			   //build pipes for sending out the REST server responses
			   ////////////////////////       
			   Pipe<ServerResponseSchema>[] netResponse = null;
			   if ((this.initFeatures & NET_RESPONDER) != 0) {
				   //int parallelInstanceId = hardware.ac
				   if (-1 == parallelInstanceId) {
					   //we have only a single instance of this object so we must have 1 pipe for each parallel track
					   int p = builder.parallelTracks();
					   netResponse = ( Pipe<ServerResponseSchema>[])new Pipe[p];
					   while (--p>=0) {
						   netResponse[p] = builder.newNetResponsePipe(pcm.getConfig(ServerResponseSchema.class), p);
					   }
				   } else {
					   //we have multiple instances of this object so each only has 1 pipe
					   netResponse = ( Pipe<ServerResponseSchema>[])new Pipe[1];
					   netResponse[0] = builder.newNetResponsePipe(pcm.getConfig(ServerResponseSchema.class), parallelInstanceId);
				   }
			   }
			   this.netResponse = netResponse;
			   ///////////////////////////
			   
			   
			   if (null != this.netResponse) {
				   
				   int x = this.netResponse.length;
	 
				   while(--x>=0) {
				   
			    	   if (!Pipe.isInit(netResponse[x])) {
			    		   //hack for now.
			    		   netResponse[x].initBuffers();
			    	   }
				   }
			   }
	
	
			   
			   ///////////////////
	
			   int e = this.exclusiveTopics.length;
			   this.exclusivePubSub = (Pipe<MessagePubSub>[])new Pipe[e];
			   while (--e>=0) {
				   exclusivePubSub[e] = newPubSubPipe(pcm.getConfig(MessagePubSub.class), builder);
			   }
			   ///////////////////       
	
			   ////////////////////////////////////////
			   int temp = 0;
			   if (null!=netResponse && netResponse.length>0) {
				   int x = netResponse.length;
				   int min = Integer.MAX_VALUE;
				   while (--x>=0) {
					   min = Math.min(min, netResponse[x].maxVarLen);
				   }
				   temp= min;
			   }
			   this.maxHTTPContentLength = temp;		
			   ///////////////////////////////////////
		   }
	}

	/**
	 *
	 * @return null if goPipe == null else PipeWriter.hasRoomForWrite(goPipe)
	 */
	public boolean goHasRoom() {
		return null==goPipe || PipeWriter.hasRoomForWrite(goPipe);
	}

	/**
	 *
	 * @return results
	 */
	public Pipe<?>[] getOutputPipes() {
    	
    	//we wait till this last possible moment before building.
    	buildAllPipes();
    	 
    	 
    	int length = 0;
    	
    	if (null != messagePubSub) { //Index needed i2c index needed.
    		length++;
    	}
    	
    	if (null != httpRequest) {
    		length++;
    	}
    	
    	if (null != netResponse) {
    		length+=netResponse.length;
    	}
    	
    	boolean hasGoSpace = false;
    	if (length>0) {//last count for go pipe
    		length++;
    		hasGoSpace = true;
    	}
    	
    	length += exclusivePubSub.length;
    	
    	if (null!=optionalOutputPipes) {
    		length+=optionalOutputPipes.length;
    	}
    	
    	if (null!=publishPrivateTopics) {
    		length+=publishPrivateTopics.count();
    	}
    	
    	//NOTE: serial store does not use GO release
    	
    	if (null!=serialStoreConsPipes) {
    		length+=serialStoreConsPipes.length;    		
    	}
    	
    	if (null!=serialStoreProdPipes) {
    		length+=serialStoreProdPipes.length;    		
    	}
    	
  
    	int idx = 0;
    	Pipe<?>[] results = new Pipe[length];
    	
    	System.arraycopy(exclusivePubSub, 0, results, 0, exclusivePubSub.length);
    	idx+=exclusivePubSub.length;
    	
    	if (null != messagePubSub) {
    		results[idx++] = messagePubSub;
    	}
    	
    	if (null != httpRequest) {
    		results[idx++] = httpRequest;
    	}
    	
    	if (null != netResponse) {    		
    		System.arraycopy(netResponse, 0, results, idx, netResponse.length);
    		idx+=netResponse.length;
    	}
    	
    	if (null!=optionalOutputPipes) {
    		System.arraycopy(optionalOutputPipes, 0, results, idx, optionalOutputPipes.length);
    		idx+=optionalOutputPipes.length;
    	}
    	
    	if (null!=publishPrivateTopics) {
    		publishPrivateTopics.copyPipes(results, idx);
    		idx+=publishPrivateTopics.count();
    	}
    	
    	if (null!=serialStoreConsPipes) {
    		System.arraycopy(serialStoreConsPipes, 0, results, idx, serialStoreConsPipes.length);
    		idx+=serialStoreConsPipes.length; 		
    	}
    	
    	if (null!=serialStoreProdPipes) {
    		System.arraycopy(serialStoreProdPipes, 0, results, idx, serialStoreProdPipes.length);
    		idx+=serialStoreProdPipes.length;  		
    	}    	
    	
    	if (hasGoSpace) {//last pipe for go, may be null
    		results[idx++] = goPipe;
    	}
    	
    	return results;
    }
    
    
    private static <B extends BuilderImpl> Pipe<MessagePubSub> newPubSubPipe(PipeConfig<MessagePubSub> config, B builder) {
    	//TODO: need to create these pipes with constants for the topics we can avoid the copy...
    	//      this will add a new API where a constant can be used instead of a topic string...
    	//      in many cases this change will double the throughput or do even better.
    	
    	if (builder.isAllPrivateTopics()) {
    		return null;
    	} else {
	    	return new Pipe<MessagePubSub>(config) {
				@Override
				protected DataOutputBlobWriter<MessagePubSub> createNewBlobWriter() {
					return new PubSubWriter(this);
				}    		
	    	};
    	}
    }
    
    private static <B extends BuilderImpl> Pipe<ClientHTTPRequestSchema> newNetRequestPipe(PipeConfig<ClientHTTPRequestSchema> config, B builder) {

    	return new Pipe<ClientHTTPRequestSchema>(config) {
			@Override
			protected DataOutputBlobWriter<ClientHTTPRequestSchema> createNewBlobWriter() {
				return new PayloadWriter<ClientHTTPRequestSchema>(this);
			}    		
    	};
    }


	private Pipe<TrafficOrderSchema> newGoPipe(PipeConfig<TrafficOrderSchema> goPipeConfig) {
		return new Pipe<TrafficOrderSchema>(goPipeConfig);
	}


	/**
	 *
	 * @param c MsgCommandChannel arg
	 * @param listener Behavior arg used to set c.listener
	 */
	public static void setListener(MsgCommandChannel<?> c, Behavior listener) {
        if (null != c.listener && c.listener!=listener) {
            throw new UnsupportedOperationException("Bad Configuration, A CommandChannel can only be held and used by a single listener lambda/class");
        }
        c.listener = listener;        
    }


    protected boolean enterBlockOk() {
        return aBool.compareAndSet(false, true);
    }
    
    protected boolean exitBlockOk() {
        return aBool.compareAndSet(true, false);
    }

    @Deprecated
    public final boolean shutdown() {
        assert(enterBlockOk()) : "Concurrent usage error, ensure this never called concurrently";
        try {
            if (goHasRoom()) {            	
            	if (null!=goPipe) {
            		PipeWriter.publishEOF(this.goPipe);            		
            	} else {
            		//must find one of these outputs to shutdown
            		if (!sentEOF(messagePubSub)) {
            			if (!sentEOF(httpRequest)) {
            				if (!sentEOF(netResponse)) {
            					if (!sentEOFPrivate()) {
            						secondShutdownMsg();
            					}
            				}            				
            			}
            		}
            	}
                return true;
            } else {
                return false;
            }
        } finally {
            assert(exitBlockOk()) : "Concurrent usage error, ensure this never called concurrently";      
        }
    }

    //can be overridden to add shutdown done by another service.
	protected void secondShutdownMsg() {
		
		//if pubsub or request or response is supported any can be used for shutdown
		logger.warn("Unable to shutdown, no supported services found...");
	
	}

	boolean sentEOFPrivate() {
		boolean ok = false;
		if (null!=publishPrivateTopics) {
			int c = publishPrivateTopics.count();
			while (--c>=0) {				
				Pipe.publishEOF(publishPrivateTopics.getPipe(c));
				ok = true;
			}
			
		}
		return ok;
	}
    
    protected boolean sentEOF(Pipe<?> pipe) {
		if (null!=pipe) {
			PipeWriter.publishEOF(pipe);
			return true;		
		} else {
			return false;
		}
	}

    protected boolean sentEOF(Pipe<?>[] pipes) {
		if (null != pipes) {
			int i = pipes.length;
			while (--i>=0) {
				if (null!=pipes[i]) {
					PipeWriter.publishEOF(pipes[i]);
					return true;
				}
			}	
		}
		return false;
	}

    @Deprecated
    public boolean block(long durationNanos) {
    	return delay(durationNanos);
    }    
    
    @Deprecated
    public boolean blockUntil(long msTime) {
    	return delayUntil(msTime);
    }
    
    
	/**
     * Causes this channel to delay processing any actions until the specified
     * amount of time has elapsed.
     *
     * @param durationNanos Nanos to delay
     *
     * @return True if blocking was successful, and false otherwise.
     */
    @Deprecated
    public boolean delay(long durationNanos) {
        assert(enterBlockOk()) : "Concurrent usage error, ensure this never called concurrently";
        try {
            if (goHasRoom()) {
            	MsgCommandChannel.publishBlockChannel(durationNanos, this);
                return true;
            } else {
                return false;
            }
        } finally {
            assert(exitBlockOk()) : "Concurrent usage error, ensure this never called concurrently";      
        }
    }

    /**
     * Causes this channel to delay processing any actions
     * until the specified UNIX time is reached.
     *
     * @return True if blocking was successful, and false otherwise.
     */
    @Deprecated
    public boolean delayUntil(long msTime) {
        assert(enterBlockOk()) : "Concurrent usage error, ensure this never called concurrently";
        try {
            if (goHasRoom()) {
            	MsgCommandChannel.publishBlockChannelUntil(msTime, this);
                return true;
            } else {
                return false;
            }
        } finally {
            assert(exitBlockOk()) : "Concurrent usage error, ensure this never called concurrently";      
        }
    }
//    //TODO: update the httpRequest to use the low level API.
// 
//    @Deprecated
//    public boolean httpGet(ClientHostPortInstance session, CharSequence route) {
//    	return httpGet(session,route,null);
//    }
//    
//    @Deprecated
//	public boolean httpGet(ClientHostPortInstance session, CharSequence route, HeaderWritable headers) {
//
//		assert(builder.getHTTPClientConfig() != null);
//		assert((this.initFeatures & NET_REQUESTER)!=0) : "must turn on NET_REQUESTER to use this method";
//
//		//////////////////////
//		//get the cached connection ID so we need not deal with the host again
//		/////////////////////
//		if (session.getConnectionId()<0) {
//			builder.getClientCoordinator();
//			final long id = ClientCoordinator.lookup(
//					                   ClientCoordinator.lookupHostId((CharSequence) session.host, READER), 
//					                   session.port, 
//					                   session.sessionId);
//		    if (id>=0) {
//		    	session.setConnectionId(id);
//		    }
//			
//			
//		} 
//
//		if ((null==goPipe || PipeWriter.hasRoomForWrite(goPipe))) {
//
//			if (session.getConnectionId()<0) {
//		
//				if (PipeWriter.tryWriteFragment(httpRequest, ClientHTTPRequestSchema.MSG_HTTPGET_100)) {
//					PipeWriter.writeInt(httpRequest, ClientHTTPRequestSchema.MSG_HTTPGET_100_FIELD_DESTINATION_11, builder.lookupHTTPClientPipe(session.sessionId));
//					PipeWriter.writeInt(httpRequest, ClientHTTPRequestSchema.MSG_HTTPGET_100_FIELD_SESSION_10, session.sessionId);
//					
//		    		PipeWriter.writeInt(httpRequest, ClientHTTPRequestSchema.MSG_HTTPGET_100_FIELD_PORT_1, session.port);
//		    		PipeWriter.writeBytes(httpRequest, ClientHTTPRequestSchema.MSG_HTTPGET_100_FIELD_HOST_2, session.hostBytes);
//		    		
//		    		PipeWriter.writeUTF8(httpRequest, ClientHTTPRequestSchema.MSG_HTTPGET_100_FIELD_PATH_3, route);
//					
//					DataOutputBlobWriter<ClientHTTPRequestSchema> hw = Pipe.outputStream(httpRequest);
//				    DataOutputBlobWriter.openField(hw);
//				    if (null!=headers) {
//				    	headers.write(headerWriter.target(hw));
//				    }
//				    hw.closeHighLevelField(ClientHTTPRequestSchema.MSG_HTTPGET_100_FIELD_HEADERS_7);
//					
//					PipeWriter.publishWrites(httpRequest);
//					
//					publishGo(1, builder.netIndex(), this);
//					
//					return true;
//				}
//			} else {
//				if (PipeWriter.tryWriteFragment(httpRequest, ClientHTTPRequestSchema.MSG_FASTHTTPGET_200)) {
//					PipeWriter.writeInt(httpRequest, ClientHTTPRequestSchema.MSG_FASTHTTPGET_200_FIELD_DESTINATION_11, builder.lookupHTTPClientPipe(session.sessionId));
//					PipeWriter.writeInt(httpRequest, ClientHTTPRequestSchema.MSG_FASTHTTPGET_200_FIELD_SESSION_10, session.sessionId);
//					
//		    		PipeWriter.writeInt(httpRequest, ClientHTTPRequestSchema.MSG_FASTHTTPGET_200_FIELD_PORT_1, session.port);
//		    		PipeWriter.writeBytes(httpRequest, ClientHTTPRequestSchema.MSG_FASTHTTPGET_200_FIELD_HOST_2, session.hostBytes);
//		    		PipeWriter.writeLong(httpRequest, ClientHTTPRequestSchema.MSG_FASTHTTPGET_200_FIELD_CONNECTIONID_20, session.getConnectionId());
//		    		PipeWriter.writeUTF8(httpRequest, ClientHTTPRequestSchema.MSG_FASTHTTPGET_200_FIELD_PATH_3, route);
//
//					DataOutputBlobWriter<ClientHTTPRequestSchema> hw = Pipe.outputStream(httpRequest);
//				    DataOutputBlobWriter.openField(hw);
//				    if (null!=headers) {
//				    	headers.write(headerWriter.target(hw));
//				    }
//				    hw.closeHighLevelField(ClientHTTPRequestSchema.MSG_FASTHTTPGET_200_FIELD_HEADERS_7);
//										
//					PipeWriter.publishWrites(httpRequest);
//					
//					publishGo(1, builder.netIndex(), this);
//					
//					return true;
//				}
//				
//			}
//			
//        }
//        return false;
//	}
//
//    @Deprecated
//	public boolean httpClose(ClientHostPortInstance session) {
//		assert(builder.getHTTPClientConfig() != null);
//		assert((this.initFeatures & NET_REQUESTER)!=0) : "must turn on NET_REQUESTER to use this method";
//		
//		if ((null==goPipe || PipeWriter.hasRoomForWrite(goPipe)) 
//			&& PipeWriter.tryWriteFragment(httpRequest, ClientHTTPRequestSchema.MSG_CLOSE_104)) {
//                	    
//			PipeWriter.writeInt(httpRequest, ClientHTTPRequestSchema.MSG_CLOSE_104_FIELD_SESSION_10, session.sessionId);
//			PipeWriter.writeInt(httpRequest, ClientHTTPRequestSchema.MSG_CLOSE_104_FIELD_PORT_1, session.port);
//			PipeWriter.writeBytes(httpRequest, ClientHTTPRequestSchema.MSG_CLOSE_104_FIELD_HOST_2, session.hostBytes);
//		
//    		PipeWriter.publishWrites(httpRequest);
//                		
//    		publishGo(1, builder.netIndex(), this);
//    		    	            
//            return true;
//        }        
//        return false;
//	}
	
	final HeaderWriter headerWriter = new HeaderWriter();//used in each post call.
	
//	@Deprecated
//	public boolean httpPost(ClientHostPortInstance session, CharSequence route, Writable payload) {
//		return httpPost(session, route, null, payload);
//	}	
//   
//	@Deprecated
//	public boolean httpPost(ClientHostPortInstance session, CharSequence route, HeaderWritable headers, Writable payload) {
//		
//		assert((this.initFeatures & NET_REQUESTER)!=0) : "must turn on NET_REQUESTER to use this method";
//		
//		if (null==goPipe || PipeWriter.hasRoomForWrite(goPipe)) { 
//						
//			if (session.getConnectionId()<0) {
//				if (PipeWriter.tryWriteFragment(httpRequest, ClientHTTPRequestSchema.MSG_HTTPPOST_101)) {
//					
//					PipeWriter.writeInt(httpRequest, ClientHTTPRequestSchema.MSG_HTTPPOST_101_FIELD_DESTINATION_11, builder.lookupHTTPClientPipe(session.sessionId));
//					PipeWriter.writeInt(httpRequest, ClientHTTPRequestSchema.MSG_HTTPPOST_101_FIELD_SESSION_10, session.sessionId);
//					PipeWriter.writeInt(httpRequest, ClientHTTPRequestSchema.MSG_HTTPPOST_101_FIELD_PORT_1, session.port);
//					PipeWriter.writeBytes(httpRequest, ClientHTTPRequestSchema.MSG_HTTPPOST_101_FIELD_HOST_2, session.hostBytes);
//					PipeWriter.writeUTF8(httpRequest, ClientHTTPRequestSchema.MSG_HTTPPOST_101_FIELD_PATH_3, route);
//					
//					DataOutputBlobWriter<ClientHTTPRequestSchema> hw = Pipe.outputStream(httpRequest);
//					DataOutputBlobWriter.openField(hw);
//					if (null!=headers) {
//						headers.write(headerWriter.target(hw));
//					}
//					hw.closeHighLevelField(ClientHTTPRequestSchema.MSG_HTTPPOST_101_FIELD_HEADERS_7);
//					
//					DataOutputBlobWriter<ClientHTTPRequestSchema> pw = Pipe.outputStream(httpRequest);
//					DataOutputBlobWriter.openField(pw);
//					payload.write(pw);
//					pw.closeHighLevelField(ClientHTTPRequestSchema.MSG_HTTPPOST_101_FIELD_PAYLOAD_5);
//					
//					PipeWriter.publishWrites(httpRequest);
//					
//					publishGo(1, builder.netIndex(), this);
//					
//					return true;
//				}
//				
//			} else {
//				
//				if (PipeWriter.tryWriteFragment(httpRequest, ClientHTTPRequestSchema.MSG_FASTHTTPPOST_201)) {
//					
//					PipeWriter.writeInt(httpRequest, ClientHTTPRequestSchema.MSG_FASTHTTPPOST_201_FIELD_DESTINATION_11, builder.lookupHTTPClientPipe(session.sessionId));
//					PipeWriter.writeInt(httpRequest, ClientHTTPRequestSchema.MSG_FASTHTTPPOST_201_FIELD_SESSION_10, session.sessionId);
//					PipeWriter.writeInt(httpRequest, ClientHTTPRequestSchema.MSG_FASTHTTPPOST_201_FIELD_PORT_1, session.port);
//					PipeWriter.writeBytes(httpRequest, ClientHTTPRequestSchema.MSG_FASTHTTPPOST_201_FIELD_HOST_2, session.hostBytes);
//					
//					PipeWriter.writeLong(httpRequest, ClientHTTPRequestSchema.MSG_FASTHTTPPOST_201_FIELD_CONNECTIONID_20, session.getConnectionId());
//										
//					PipeWriter.writeUTF8(httpRequest, ClientHTTPRequestSchema.MSG_FASTHTTPPOST_201_FIELD_PATH_3, route);
//					
//					DataOutputBlobWriter<ClientHTTPRequestSchema> hw = Pipe.outputStream(httpRequest);
//					DataOutputBlobWriter.openField(hw);
//					if (null!=headers) {
//						headers.write(headerWriter.target(hw));
//					}
//					hw.closeHighLevelField(ClientHTTPRequestSchema.MSG_FASTHTTPPOST_201_FIELD_HEADERS_7);
//					
//					DataOutputBlobWriter<ClientHTTPRequestSchema> pw = Pipe.outputStream(httpRequest);
//					DataOutputBlobWriter.openField(pw);
//					payload.write(pw);
//					pw.closeHighLevelField(ClientHTTPRequestSchema.MSG_FASTHTTPPOST_201_FIELD_PAYLOAD_5);
//					
//					PipeWriter.publishWrites(httpRequest);
//					
//					publishGo(1, builder.netIndex(), this);
//					
//					return true;
//				}
//				
//				
//			}
//		    
//		    
//		} 
//		return false;
//	}
//
//    /**
//     * Subscribes the listener associated with this command channel to
//     * a topic.
//     *
//     * @param topic Topic to subscribe to.
//     *
//     * @return True if the topic was successfully subscribed to, and false
//     *         otherwise.
//     */
//	@Deprecated
//    public boolean subscribe(CharSequence topic) {
//    			
//		assert((0 != (initFeatures & DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
//    	
//    	if (null==listener) {
//    		throw new UnsupportedOperationException("Can not subscribe before startup. Call addSubscription when registering listener."); 
//    	}
//    	
//        return subscribe(topic, (PubSubMethodListenerBase)listener);
//    }
//
//    /**
//     * Subscribes a listener to a topic on this command channel.
//     *
//     * @param topic Topic to subscribe to.
//     * @param listener Listener to subscribe.
//     *
//     * @return True if the topic was successfully subscribed to, and false
//     *         otherwise.
//     */
//	@Deprecated
//    public boolean subscribe(CharSequence topic, PubSubMethodListenerBase listener) {
//		assert((0 != (initFeatures & DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
//
//        if ((null==goPipe || PipeWriter.hasRoomForWrite(goPipe)) 
//        	&& PipeWriter.tryWriteFragment(messagePubSub, MessagePubSub.MSG_SUBSCRIBE_100)) {
//            
//            PipeWriter.writeInt(messagePubSub, MessagePubSub.MSG_SUBSCRIBE_100_FIELD_SUBSCRIBERIDENTITYHASH_4, System.identityHashCode(listener));
//            //OLD -- PipeWriter.writeUTF8(messagePubSub, MessagePubSub.MSG_SUBSCRIBE_100_FIELD_TOPIC_1, topic);
//            
//            DataOutputBlobWriter<MessagePubSub> output = PipeWriter.outputStream(messagePubSub);
//    		output.openField();	    		
//    		output.append(topic);
//    		
//    		publicTrackedTopicSuffix(this, output);
//    		
//    		output.closeHighLevelField(MessagePubSub.MSG_SUBSCRIBE_100_FIELD_TOPIC_1);
//            
//            PipeWriter.publishWrites(messagePubSub);
//            
//            builder.releasePubSubTraffic(1, this);
//            
//            return true;
//        }        
//        return false;
//    }
//
//    /**
//     * Unsubscribes the listener associated with this command channel from
//     * a topic.
//     *
//     * @param topic Topic to unsubscribe from.
//     *
//     * @return True if the topic was successfully unsubscribed from, and false otherwise.
//     */
//	@Deprecated
//    public boolean unsubscribe(CharSequence topic) {
//		assert((0 != (initFeatures & DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
//
//        return unsubscribe(topic, (PubSubMethodListenerBase)listener);
//    }
//
//    /**
//     * Unsubscribes a listener from a topic.
//     *
//     * @param topic Topic to unsubscribe from.
//     * @param listener Listener to unsubscribe.
//     *
//     * @return True if the topic was successfully unsubscribed from, and false otherwise.
//     */
//	@Deprecated
//    public boolean unsubscribe(CharSequence topic, PubSubMethodListenerBase listener) {
//		 assert((0 != (initFeatures & DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
//
//        if ((null==goPipe || PipeWriter.hasRoomForWrite(goPipe)) 
//        	&& PipeWriter.tryWriteFragment(messagePubSub, MessagePubSub.MSG_UNSUBSCRIBE_101)) {
//            
//            PipeWriter.writeInt(messagePubSub, MessagePubSub.MSG_SUBSCRIBE_100_FIELD_SUBSCRIBERIDENTITYHASH_4, System.identityHashCode(listener));
//           //OLD  PipeWriter.writeUTF8(messagePubSub, MessagePubSub.MSG_UNSUBSCRIBE_101_FIELD_TOPIC_1, topic);
//            DataOutputBlobWriter<MessagePubSub> output = PipeWriter.outputStream(messagePubSub);
//    		output.openField();	    		
//    		output.append(topic);
//    		
//    		publicTrackedTopicSuffix(this, output);
//    		
//    		output.closeHighLevelField(MessagePubSub.MSG_UNSUBSCRIBE_101_FIELD_TOPIC_1);
//            
//            PipeWriter.publishWrites(messagePubSub);
//            
//            builder.releasePubSubTraffic(1, this);
//            
//            return true;
//        }        
//        return false;
//    }
//
//    /**
//     * Changes the state of this command channel's state machine.
//     *
//     * @param state State to transition to.
//     *
//     * @return True if the state was successfully transitioned, and false otherwise.
//     */
//	@Deprecated
//    public <E extends Enum<E>> boolean changeStateTo(E state) {
//		 assert((0 != (initFeatures & DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
//
//    	 assert(builder.isValidState(state));
//    	 if (!builder.isValidState(state)) {
//    		 throw new UnsupportedOperationException("no match "+state.getClass());
//    	 }
//    	
//    	 if ((null==goPipe || PipeWriter.hasRoomForWrite(goPipe)) 
//    	     && PipeWriter.tryWriteFragment(messagePubSub, MessagePubSub.MSG_CHANGESTATE_70)) {
//
//    		 PipeWriter.writeInt(messagePubSub, MessagePubSub.MSG_CHANGESTATE_70_FIELD_ORDINAL_7,  state.ordinal());
//             PipeWriter.publishWrites(messagePubSub);
//             
//             builder.releasePubSubTraffic(1, this);
//    		 return true;
//    	 }
//
//    	return false;
//    	
//    }
//
//    @Deprecated
//    public void presumePublishTopic(CharSequence topic, Writable writable) {
//    	presumePublishTopic(topic, writable, WaitFor.All);
//    }
//    
//    @Deprecated
//    public void presumePublishTopic(CharSequence topic, Writable writable, WaitFor ap) {
//    	assert((0 != (initFeatures & DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
//    	
//    	if (!publishTopic(topic, writable, ap)) {
//			logger.warn("unable to publish on topic {} must wait.",topic);
//			while (!publishTopic(topic, writable, ap)) {
//				Thread.yield();
//			}
//		}
//    }
    
    //TODO: create object to wrap immutable topic and to cache its details
    //      removes trie parser lookup of private topic plus UTF8 conversion
    //      streamline the new hot spot in profiler!!!!
    
    String cachedTopic="";
    int    cachedTopicToken=-2;
    
//    @Deprecated
//    public boolean publishTopic(CharSequence topic, Writable writable) {
//    	return publishTopic(topic, writable, WaitFor.All);
//    }
//    /**
//     * Opens a topic on this channel for writing.
//     *
//     * @param topic Topic to open.
//     *
//     * @return {@link PayloadWriter} attached to the given topic.
//     */
//    @Deprecated
//    public boolean publishTopic(CharSequence topic, Writable writable, WaitFor ap) {
//		assert((0 != (initFeatures & DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
//        assert(writable != null);
//
//        
//        ///////////////////////////////////////////////////
//        //hack test for now to see if this is worth doing.
//        //NOTE: this is not helping much because HTTP header parsing dwarfs this work.
//        int token;
//        if (topic instanceof String) {
//        	if (topic == cachedTopic) {
//        		token = cachedTopicToken;
//        	} else {
//        		token =  null==publishPrivateTopics ? -1 : publishPrivateTopics.getToken(topic);
//        		cachedTopic = (String)topic;
//        		cachedTopicToken = token;
//        	}
//        } else {
//        	token =  null==publishPrivateTopics ? -1 : publishPrivateTopics.getToken(topic);	
//        }
//        //////////////////////////
//        
//		
//		if (token>=0) {
//			return publishOnPrivateTopic(token, writable);
//		} else {
//	        if ((null==goPipe || PipeWriter.hasRoomForWrite(goPipe)) && 
//	        	PipeWriter.tryWriteFragment(messagePubSub, MessagePubSub.MSG_PUBLISH_103)) {
//	    		
//	    		PipeWriter.writeInt(messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_QOS_5, ap.policy());
//	        	//PipeWriter.writeUTF8(messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1, topic);         
//	
//	        	DataOutputBlobWriter<MessagePubSub> output = PipeWriter.outputStream(messagePubSub);
//	     		output.openField();	    		
//	     		output.append(topic);	     		
//	     		publicTrackedTopicSuffix(this, output);
//	     		output.closeHighLevelField(MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1);
//	     		
//	            PubSubWriter pw = (PubSubWriter) Pipe.outputStream(messagePubSub);	           
//	        	DataOutputBlobWriter.openField(pw);
//	        	writable.write(pw);
//	            DataOutputBlobWriter.closeHighLevelField(pw, MessagePubSub.MSG_PUBLISH_103_FIELD_PAYLOAD_3);
//	            
//	            PipeWriter.publishWrites(messagePubSub);
//	
//	            publishGo(1,builder.pubSubIndex(), this);
//	                        
//	            
//	            return true;
//	            
//	        } else {
//	            return false;
//	        }
//		}
//    }
//
//    @Deprecated
//	public FailableWrite publishFailableTopic(CharSequence topic, FailableWritable writable) {
//    	return publishFailableTopic(topic, writable, WaitFor.All);
//	}
//
//    @Deprecated
//	public FailableWrite publishFailableTopic(CharSequence topic, FailableWritable writable, WaitFor ap) {
//		assert((0 != (initFeatures & DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
//		assert(writable != null);
//
//		int token =  null==publishPrivateTopics ? -1 : publishPrivateTopics.getToken(topic);
//
//		if (token>=0) {
//			return publishFailableOnPrivateTopic(token, writable);
//		} else {
//			if ((null==goPipe || PipeWriter.hasRoomForWrite(goPipe)) && PipeWriter.hasRoomForWrite(messagePubSub)) {
//				PubSubWriter pw = (PubSubWriter) Pipe.outputStream(messagePubSub);
//
//				DataOutputBlobWriter.openField(pw);
//				FailableWrite result = writable.write(pw);
//
//				if (result == FailableWrite.Cancel) {
//					messagePubSub.closeBlobFieldWrite();
//				}
//				else {
//					PipeWriter.presumeWriteFragment(messagePubSub, MessagePubSub.MSG_PUBLISH_103);
//					PipeWriter.writeInt(messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_QOS_5, ap.policy());
//					
//		    		DataOutputBlobWriter<MessagePubSub> output = PipeWriter.outputStream(messagePubSub);
//		    		output.openField();	    		
//		    		output.append(topic);
//		    		
//		    		publicTrackedTopicSuffix(this, output);
//		    		
//		    		output.closeHighLevelField(MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1);
//										
//					//OLD PipeWriter.writeUTF8(messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1, topic);
//
//					DataOutputBlobWriter.closeHighLevelField(pw, MessagePubSub.MSG_PUBLISH_103_FIELD_PAYLOAD_3);
//
//					PipeWriter.publishWrites(messagePubSub);
//
//					publishGo(1, builder.pubSubIndex(), this);
//				}
//				return result;
//			} else {
//				return FailableWrite.Retry;
//			}
//		}
//	}
//
//    @Deprecated
//    public boolean publishTopic(CharSequence topic) {
//    	return publishTopic(topic, WaitFor.All);
//    }
//    
//    @Deprecated
//    public boolean publishTopic(CharSequence topic, WaitFor ap) {
//		assert((0 != (initFeatures & DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
//
//		int token =  null==publishPrivateTopics ? -1 : publishPrivateTopics.getToken(topic);
//
//		if (token>=0) {
//			return publishOnPrivateTopic(token);
//		} else {
//			if (null==messagePubSub) {
//				if (builder.isAllPrivateTopics()) {
//					throw new RuntimeException("Discovered non private topic '"+topic+"' but exclusive use of private topics was set on.");
//				} else {
//					throw new RuntimeException("Enable DYNAMIC_MESSAGING for this CommandChannel before publishing.");
//				}
//			}
//			
//	        if ((null==goPipe || PipeWriter.hasRoomForWrite(goPipe)) && 
//	        	PipeWriter.tryWriteFragment(messagePubSub, MessagePubSub.MSG_PUBLISH_103)) {
//	    		
//	    		PipeWriter.writeInt(messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_QOS_5, ap.policy());
//	        	
//	    		DataOutputBlobWriter<MessagePubSub> output = PipeWriter.outputStream(messagePubSub);
//	    		output.openField();	    		
//	    		output.append(topic);
//	    		
//	    		publicTrackedTopicSuffix(this, output);
//	    		
//	    		output.closeHighLevelField(MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1);
//	    		
//	    		////OLD: PipeWriter.writeUTF8(messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1, topic);         
//	 
//	        	
//	        	
//				PipeWriter.writeSpecialBytesPosAndLen(messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_PAYLOAD_3, -1, 0);
//				PipeWriter.publishWrites(messagePubSub);
//	
//	            publishGo(1,builder.pubSubIndex(), this);
//	                        
//	            return true;
//	            
//	        } else {
//	            return false;
//	        }
//		}
//		
//    }

    
    static final void publicTrackedTopicSuffix(MsgCommandChannel cmd, DataOutputBlobWriter<MessagePubSub> output) {
    	if (null==cmd.track) { //most command channels are assumed to be un tracked
    		//nothing to do.
    	} else {
    		trackedChannelSuffix(cmd, output);
    	}
	}

	private static void trackedChannelSuffix(MsgCommandChannel cmd, DataOutputBlobWriter<MessagePubSub> output) {
		if (BuilderImpl.hasNoUnscopedTopics()) {//normal case where topics are scoped
			output.write(cmd.track);
		} else {
			unScopedCheckForTrack(cmd, output);
		}
	}

	private static void unScopedCheckForTrack(MsgCommandChannel cmd, DataOutputBlobWriter<MessagePubSub> output) {
		boolean addSuffix=false;
				
		addSuffix = BuilderImpl.notUnscoped(TrieParserReaderLocal.get(), output);
		
		if (addSuffix) {
			output.write(cmd.track);			
		}
		
	}
//
//	@Deprecated
//	public boolean publishTopic(byte[] topic, Writable writable) {
//		assert((0 != (initFeatures & DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
//        assert(writable != null);
// 
//        int token =  null==publishPrivateTopics ? -1 : publishPrivateTopics.getToken(topic, 0, topic.length);
//		 
//        if (token>=0) {
//        	return publishOnPrivateTopic(token, writable);
//        } else {
//        	//should not be called when	DYNAMIC_MESSAGING is not on.
//        	
//	        //this is a public topic
//        	if ((null==goPipe || PipeWriter.hasRoomForWrite(goPipe)) && 
//	        	PipeWriter.tryWriteFragment(messagePubSub, MessagePubSub.MSG_PUBLISH_103)) {
//	            
//        		DataOutputBlobWriter<MessagePubSub> output = PipeWriter.outputStream(messagePubSub);
//	    		output.openField();	
//	    		output.write(topic);
//	    		publicTrackedTopicSuffix(this, output);    		
//	    		
//	    		output.closeHighLevelField(MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1);
//        		
//	        	//OLD PipeWriter.writeBytes(messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1, topic);         
//	        	
//	            PubSubWriter writer = (PubSubWriter) Pipe.outputStream(messagePubSub);
//	            
//	            writer.openField(MessagePubSub.MSG_PUBLISH_103_FIELD_PAYLOAD_3,this);
//	            writable.write(writer);
//	            writer.publish();
//	            publishGo(1,builder.pubSubIndex(), this);
//	                        
//	            return true;
//	            
//	        } else {
//	            return false;
//	        }
//        }
//    }
//
//	@Deprecated
//    public boolean publishTopic(byte[] topic) {
//		assert((0 != (initFeatures & DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
// 
//		int token =  null==publishPrivateTopics ? -1 : publishPrivateTopics.getToken(topic, 0, topic.length);
//
//        if (token>=0) {
//        	return publishOnPrivateTopic(token);
//        } else {
//        	//should not be called when	DYNAMIC_MESSAGING is not on.
//        	
//	        //this is a public topic
//        	if ((null==goPipe || PipeWriter.hasRoomForWrite(goPipe)) && 
//	        	PipeWriter.tryWriteFragment(messagePubSub, MessagePubSub.MSG_PUBLISH_103)) {
//	            
//        		DataOutputBlobWriter<MessagePubSub> output = PipeWriter.outputStream(messagePubSub);
//	    		output.openField();	
//	    		output.write(topic);
//	    		publicTrackedTopicSuffix(this, output);	    		
//	    		
//	    		output.closeHighLevelField(MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1);
//	        	//OLD  PipeWriter.writeBytes(messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1, topic);         
//	        		        	
//				PipeWriter.writeSpecialBytesPosAndLen(messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_PAYLOAD_3, -1, 0);
//				PipeWriter.publishWrites(messagePubSub);
//	            	            
//	            publishGo(1,builder.pubSubIndex(), this);
//	                        
//	            return true;
//	            
//	        } else {
//	            return false;
//	        }
//        }
//    }
    
	boolean publishOnPrivateTopic(int token, Writable writable) {
		//this is a private topic            
		Pipe<MessagePrivate> output = publishPrivateTopics.getPipe(token);
		if (Pipe.hasRoomForWrite(output)) {
			int size = Pipe.addMsgIdx(output, MessagePrivate.MSG_PUBLISH_1);
	
			DataOutputBlobWriter<MessagePrivate> writer = Pipe.openOutputStream(output);
			writable.write(writer);
			DataOutputBlobWriter.closeLowLevelField(writer);
			Pipe.confirmLowLevelWrite(output, size);
			Pipe.publishWrites(output);
			
			return true;
		} else {
			logPrivateTopicTooShort(token,output);
			return false;
		}
	}

	FailableWrite publishFailableOnPrivateTopic(int token, FailableWritable writable) {
		//this is a private topic
		Pipe<MessagePrivate> output = publishPrivateTopics.getPipe(token);
		if (Pipe.hasRoomForWrite(output)) {
			DataOutputBlobWriter<MessagePrivate> writer = Pipe.openOutputStream(output);
			FailableWrite result = writable.write(writer);

			if (result == FailableWrite.Cancel) {
				output.closeBlobFieldWrite();
			} else {
				int size = Pipe.addMsgIdx(output, MessagePrivate.MSG_PUBLISH_1);
				DataOutputBlobWriter.closeLowLevelField(writer);
				Pipe.confirmLowLevelWrite(output, size);
				Pipe.publishWrites(output);
			}
			return result;
		} else {
			return FailableWrite.Retry;
		}
	}
    
	boolean publishOnPrivateTopic(int token) {
		//this is a private topic            
		Pipe<MessagePrivate> output = publishPrivateTopics.getPipe(token);
		if (Pipe.hasRoomForWrite(output)) {
			int size = Pipe.addMsgIdx(output, MessagePrivate.MSG_PUBLISH_1);
			Pipe.addNullByteArray(output);
			Pipe.confirmLowLevelWrite(output, size);	
			Pipe.publishWrites(output);
			return true;
		} else {
			logPrivateTopicTooShort(token, output);
			return false;
		}
	}
	
	
	private final BloomFilter topicsTooShort = new BloomFilter(10000, .00001); //32K
	
    private void logPrivateTopicTooShort(int token, Pipe<?> p) {
    	String topic = publishPrivateTopics.getTopic(token);
    	
    	if (!topicsTooShort.mayContain(topic)) {   
    		logger.info("full pipe {}",p);
    		logger.info("the private topic '{}' has become backed up, it may be too short. When it was defined it should be made to be longer.", topic);
    		topicsTooShort.addValue(topic);
    	}
	}

//    @Deprecated
//	public void presumePublishTopic(TopicWritable topic, Writable writable) {
//    	presumePublishTopic(topic,writable,WaitFor.All);
//    }    
//    
//    @Deprecated
//    public void presumePublishTopic(TopicWritable topic, Writable writable, WaitFor ap) {
//    	assert((0 != (initFeatures & DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
//
//    	if (publishTopic(topic, writable, ap)) {
//			return;
//		} else { 
//			logger.warn("unable to publish on topic {} must wait.",topic);
//			while (!publishTopic(topic, writable, ap)) {
//				Thread.yield();
//			}
//		}
//    }
//    
//    @Deprecated
//    public boolean publishTopic(TopicWritable topic, Writable writable) {
//    	return publishTopic(topic, writable, WaitFor.All);    	
//    }
//    
//    @Deprecated
//    public boolean publishTopic(TopicWritable topic, Writable writable, WaitFor ap) {
//		assert((0 != (initFeatures & DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
//        assert(writable != null);
//        
//		int token = tokenForPrivateTopic(topic);
//		
//		if (token>=0) {
//			return publishOnPrivateTopic(token, writable);
//		} else { 
//	        if ((null==goPipe || PipeWriter.hasRoomForWrite(goPipe)) && 
//	        	PipeWriter.tryWriteFragment(messagePubSub, MessagePubSub.MSG_PUBLISH_103)) {
//	        	
//	    		
//	    		PipeWriter.writeInt(messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_QOS_5, ap.policy());
//	
//	            
//	            PubSubWriter pw = (PubSubWriter) Pipe.outputStream(messagePubSub);
//	        	DataOutputBlobWriter.openField(pw);
//	        	topic.write(pw);
//	     		publicTrackedTopicSuffix(this, pw);
//	        	DataOutputBlobWriter.closeHighLevelField(pw, MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1);
//	           
//	        	DataOutputBlobWriter.openField(pw);
//	        	writable.write(pw);
//	            DataOutputBlobWriter.closeHighLevelField(pw, MessagePubSub.MSG_PUBLISH_103_FIELD_PAYLOAD_3);
//	            
//	            PipeWriter.publishWrites(messagePubSub);
//	
//	            publishGo(1,builder.pubSubIndex(), this);
//	            
//	                        
//	            return true;
//	            
//	        } else {
//	            return false;
//	        }
//		}
//    }
    
    private final int maxDynamicTopicLength = 128;
    private Pipe<RawDataSchema> tempTopicPipe;
        
//    @Deprecated
//    public boolean publishTopic(TopicWritable topic) {
//    	return publishTopic(topic, WaitFor.All);
//    }
//    
//    @Deprecated
//    public boolean publishTopic(TopicWritable topic, WaitFor ap) {
//		assert((0 != (initFeatures & DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
//        
//		int token = tokenForPrivateTopic(topic);
//		
//		if (token>=0) {
//			return publishOnPrivateTopic(token);
//		} else {
//	        if ((null==goPipe || PipeWriter.hasRoomForWrite(goPipe)) && 
//	        	PipeWriter.tryWriteFragment(messagePubSub, MessagePubSub.MSG_PUBLISH_103)) {
//	        	    		
//	    		PipeWriter.writeInt(messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_QOS_5, ap.policy());
//	        	
//	        	PubSubWriter pw = (PubSubWriter) Pipe.outputStream(messagePubSub);
//	        	DataOutputBlobWriter.openField(pw);
//	        	topic.write(pw);
//	     		publicTrackedTopicSuffix(this, pw);
//	        	DataOutputBlobWriter.closeHighLevelField(pw, MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1);
//	                   	
//				PipeWriter.writeSpecialBytesPosAndLen(messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_PAYLOAD_3, -1, 0);
//				PipeWriter.publishWrites(messagePubSub);
//	
//	            publishGo(1,builder.pubSubIndex(), this);
//	                        
//	            return true;
//	            
//	        } else {
//	            return false;
//	        }
//		}
//    }

	int tokenForPrivateTopic(TopicWritable topic) {
		if (null==publishPrivateTopics) {
			return -1;
		}
		if (null==tempTopicPipe) {
			tempTopicPipe = RawDataSchema.instance.newPipe(2, maxDynamicTopicLength);
			tempTopicPipe.initBuffers();
		}

		int size = Pipe.addMsgIdx(tempTopicPipe, RawDataSchema.MSG_CHUNKEDSTREAM_1);
		DataOutputBlobWriter<RawDataSchema> output = Pipe.openOutputStream(tempTopicPipe);
		topic.write(output);
		DataOutputBlobWriter.closeLowLevelField(output);
		Pipe.confirmLowLevelWrite(tempTopicPipe, size);
		Pipe.publishWrites(tempTopicPipe);			
        
		Pipe.takeMsgIdx(tempTopicPipe);
		
		int token = publishPrivateTopics.getToken(tempTopicPipe);
				
		Pipe.confirmLowLevelRead(tempTopicPipe, size);
		Pipe.releaseReadLock(tempTopicPipe);
		return token;
	}
     
//    @Deprecated
//	public boolean publishHTTPResponse(HTTPFieldReader<?> reqeustReader, int statusCode) {
//		
//		 assert((0 != (initFeatures & NET_RESPONDER))) : "CommandChannel must be created with NET_RESPONDER flag";
//
//		//logger.info("Building response for connection {} sequence {} ",w.getConnectionId(),w.getSequenceCode());
//		
//		return publishHTTPResponse(reqeustReader.getConnectionId(), reqeustReader.getSequenceCode(),
//				statusCode,false,null,Writable.NO_OP); //no type and no body so use null
//	}
//
//    @Deprecated
//	public boolean publishHTTPResponse(HTTPFieldReader<?> reqeustReader, 
//            							int statusCode,
//									    HTTPContentType contentType,
//									   	Writable writable) {
//	
//		assert((0 != (initFeatures & NET_RESPONDER))) : "CommandChannel must be created with NET_RESPONDER flag";
//
//		return publishHTTPResponse(reqeustReader.getConnectionId(), reqeustReader.getSequenceCode(),
//									statusCode, false, contentType, writable);
//	}	
//
//    @Deprecated
//	public boolean publishHTTPResponse(HTTPFieldReader<?> reqeustReader, 
//									   int statusCode, boolean hasContinuation,
//									   HTTPContentType contentType,
//									   Writable writable) {
//		
//		 assert((0 != (initFeatures & NET_RESPONDER))) : "CommandChannel must be created with NET_RESPONDER flag";
//
//		return publishHTTPResponse(reqeustReader.getConnectionId(), reqeustReader.getSequenceCode(),
//				                statusCode, hasContinuation, contentType, writable);
//	}	
//
//    @Deprecated
//	public boolean publishHTTPResponse(long connectionId, long sequenceCode, int statusCode) {
//		return publishHTTPResponse(connectionId, sequenceCode, statusCode, false, null, Writable.NO_OP);
//	}
	
	///////////////////////////////////
	//these fields are needed for holding the position data for the first block of two
	//this is required so we can go back to fill in length after the second block
	//length is known
	long block1PositionOfLen;
	int block1HeaderBlobPosition;
	//this is not thread safe but works because command channels are only used by same thread
	////////////////////////////////////
	
//	@Deprecated
//	public boolean publishHTTPResponse(long connectionId, long sequenceCode, 
//			                           int statusCode, 
//			                           boolean hasContinuation,
//			                           HTTPContentType contentType,
//			                           Writable writable) {
//		
//		assert(null!=writable);
//		assert((0 != (initFeatures & NET_RESPONDER))) : "CommandChannel must be created with NET_RESPONDER flag";
//
//		final int sequenceNo = 0xFFFFFFFF & (int)sequenceCode;
//		final int parallelIndex = 0xFFFFFFFF & (int)(sequenceCode>>32);
//	
//		assert(1==lastResponseWriterFinished) : "Previous write was not ended can not start another.";
//			
//		Pipe<ServerResponseSchema> pipe = netResponse.length>1 ? netResponse[parallelIndex] : netResponse[0];
//		
//		//logger.info("try new publishHTTPResponse "+pipe);
//		if (!Pipe.hasRoomForWrite(pipe, 
//				2*Pipe.sizeOf(pipe, ServerResponseSchema.MSG_TOCHANNEL_100))) {
//			return false;
//		}		
//		//simple check to ensure we have room.
//        assert(Pipe.workingHeadPosition(pipe)<(Pipe.tailPosition(pipe)+ pipe.sizeOfSlabRing  /*    pipe.slabMask*/  )) : "Working position is now writing into published(unreleased) tail "+
//        Pipe.workingHeadPosition(pipe)+"<"+Pipe.tailPosition(pipe)+"+"+pipe.sizeOfSlabRing /*pipe.slabMask*/+" total "+((Pipe.tailPosition(pipe)+pipe.slabMask));
//
//
//		///////////////////////////////////////
//		//message 1 which contains the headers
//		//////////////////////////////////////		
//		holdEmptyBlock(connectionId, sequenceNo, pipe);
//	
//		
//		//check again because we have taken 2 spots now
//        assert(Pipe.workingHeadPosition(pipe)<(Pipe.tailPosition(pipe)+ pipe.sizeOfSlabRing  /*    pipe.slabMask*/  )) : "Working position is now writing into published(unreleased) tail "+
//        Pipe.workingHeadPosition(pipe)+"<"+Pipe.tailPosition(pipe)+"+"+pipe.sizeOfSlabRing /*pipe.slabMask*/+" total "+((Pipe.tailPosition(pipe)+pipe.slabMask));
//
//		//////////////////////////////////////////
//		//begin message 2 which contains the body
//		//////////////////////////////////////////
//
//		Pipe.addMsgIdx(pipe, ServerResponseSchema.MSG_TOCHANNEL_100);
//		Pipe.addLongValue(connectionId, pipe);
//		Pipe.addIntValue(sequenceNo, pipe);
//
//		NetResponseWriter outputStream = (NetResponseWriter)Pipe.outputStream(pipe);
//		
//		int context;
//		if (hasContinuation) {
//			context = 0;
//			lastResponseWriterFinished = 0;
//		} else {
//			context = HTTPFieldReader.END_OF_RESPONSE;
//			lastResponseWriterFinished = 1;	
//		}
//
//		//NB: context passed in here is looked at to know if this is END_RESPONSE and if so
//		//then the length is added if not then the header will designate chunked.
//		outputStream.openField(statusCode, context, contentType);
//		writable.write(outputStream); 
//		
//		if (hasContinuation) {
//			// for chunking we must end this block			
//			outputStream.write(RETURN_NEWLINE);
//		}
//		
//		outputStream.publishWithHeader(block1HeaderBlobPosition, block1PositionOfLen); //closeLowLevelField and publish 
//	
//		return true;
//
//	}
//
//	@Deprecated
//	public boolean publishHTTPResponse(long connectionId, long sequenceCode, 
//							           boolean hasContinuation,
//							           CharSequence headers,
//							           Writable writable) {
//
//				assert((0 != (initFeatures & NET_RESPONDER))) : "CommandChannel must be created with NET_RESPONDER flag";
//				
//				final int sequenceNo = 0xFFFFFFFF & (int)sequenceCode;
//				final int parallelIndex = 0xFFFFFFFF & (int)(sequenceCode>>32);
//				
//				assert(1==lastResponseWriterFinished) : "Previous write was not ended can not start another.";
//				
//				Pipe<ServerResponseSchema> pipe = netResponse.length>1 ? netResponse[parallelIndex] : netResponse[0];
//				
//				if (!Pipe.hasRoomForWrite(pipe)) {
//					return false;
//				}		
//								
//				///////////////////////////////////////
//				//message 1 which contains the headers
//				//////////////////////////////////////		
//				holdEmptyBlock(connectionId, sequenceNo, pipe);
//				
//				//////////////////////////////////////////
//				//begin message 2 which contains the body
//				//////////////////////////////////////////
//				
//				Pipe.addMsgIdx(pipe, ServerResponseSchema.MSG_TOCHANNEL_100);
//				Pipe.addLongValue(connectionId, pipe);
//				Pipe.addIntValue(sequenceNo, pipe);	
//				
//				NetResponseWriter outputStream = (NetResponseWriter)Pipe.outputStream(pipe);
//				
//				int context;
//				if (hasContinuation) {
//					context = 0;
//					lastResponseWriterFinished = 0;
//				} else {
//					context = HTTPFieldReader.END_OF_RESPONSE;
//					lastResponseWriterFinished = 1;	
//				}	
//				
//				DataOutputBlobWriter.openField(outputStream);
//				writable.write(outputStream); 
//				
//				if (hasContinuation) {
//					// for chunking we must end this block			
//					outputStream.write(RETURN_NEWLINE);
//				}
//				
//				int len = NetResponseWriter.closeLowLevelField(outputStream); //end of writing the payload    	
//				
//				Pipe.addIntValue(context, outputStream.getPipe());  //real context    	
//				Pipe.confirmLowLevelWrite(outputStream.getPipe());
//				   	
//				////////////////////Write the header
//
//				DataOutputBlobWriter.openFieldAtPosition(outputStream, block1HeaderBlobPosition);
//				
//				//HACK TODO: must formalize response building..
//				outputStream.write(HTTPRevisionDefaults.HTTP_1_1.getBytes());
//				outputStream.append(" 200 OK\r\n");
//				outputStream.append(headers);
//				outputStream.append("Content-Length: "+len+"\r\n");
//				outputStream.append("\r\n");
//				
//				//outputStream.debugAsUTF8();
//				
//				int propperLength = DataOutputBlobWriter.length(outputStream);
//				Pipe.validateVarLength(outputStream.getPipe(), propperLength);
//				Pipe.setIntValue(propperLength, outputStream.getPipe(), block1PositionOfLen); //go back and set the right length.
//				outputStream.getPipe().closeBlobFieldWrite();
//				
//				//now publish both header and payload
//				Pipe.publishWrites(outputStream.getPipe());
//				
//				return true;
//
//	}
	
	void holdEmptyBlock(long connectionId, final int sequenceNo, Pipe<ServerResponseSchema> pipe) {
	
			Pipe.addMsgIdx(pipe, ServerResponseSchema.MSG_TOCHANNEL_100);
			Pipe.addLongValue(connectionId, pipe);
			Pipe.addIntValue(sequenceNo, pipe);	
			
			NetResponseWriter outputStream = (NetResponseWriter)Pipe.outputStream(pipe);	
			block1HeaderBlobPosition = Pipe.getWorkingBlobHeadPosition(pipe);
	
			DataOutputBlobWriter.openFieldAtPosition(outputStream, block1HeaderBlobPosition); 	//no context, that will come in the second message 
	        
			//for the var field we store this as meta then length
			block1PositionOfLen = (1+Pipe.workingHeadPosition(pipe));
			
			DataOutputBlobWriter.closeLowLevelMaxVarLenField(outputStream);
			assert(pipe.maxVarLen == Pipe.slab(pipe)[((int)block1PositionOfLen) & Pipe.slabMask(pipe)]) : "expected max var field length";
			
			Pipe.addIntValue(0, pipe); 	//no context, that will come in the second message		
			//the full blob size of this message is very large to ensure we have room later...
			//this call allows for the following message to be written after this messages blob data
			int consumed = Pipe.writeTrailingCountOfBytesConsumed(outputStream.getPipe()); 
			assert(pipe.maxVarLen == consumed);
			Pipe.confirmLowLevelWrite(pipe); 
			//Stores this publish until the next message is complete and published
			Pipe.storeUnpublishedWrites(outputStream.getPipe());
	
			
			//logger.info("new empty block at {} {} ",block1HeaderBlobPosition, block1PositionOfLen);
	}
//	
//	@Deprecated
//	public boolean publishHTTPResponseContinuation(HTTPFieldReader<?> w, 
//										boolean hasContinuation, Writable writable) {
//		return publishHTTPResponseContinuation(w.getConnectionId(),w.getSequenceCode(), hasContinuation, writable);
//	}
//
//	@Deprecated
//	public boolean publishHTTPResponseContinuation(long connectionId, long sequenceCode, 
//												   boolean hasContinuation, Writable writable) {
//		
//    	assert((0 != (initFeatures & NET_RESPONDER))) : "CommandChannel must be created with NET_RESPONDER flag";
//
//		final int sequenceNo = 0xFFFFFFFF & (int)sequenceCode;
//		final int parallelIndex = 0xFFFFFFFF & (int)(sequenceCode>>32);		
//		
//		assert(0==lastResponseWriterFinished) : "Unable to find write in progress, nothing to continue with";
//		
//		Pipe<ServerResponseSchema> pipe = netResponse.length>1 ? netResponse[parallelIndex] : netResponse[0];
//
//		//logger.trace("calling publishHTTPResponseContinuation");
//		
//		if (!Pipe.hasRoomForWrite(pipe)) {
//			return false;
//		}
//		
//		
//		///////////////////////////////////////
//		//message 1 which contains the chunk length
//		//////////////////////////////////////		
//		holdEmptyBlock(connectionId, sequenceNo, pipe);
//		
//		///////////////////////////////////////
//		//message 2 which contains the chunk
//		//////////////////////////////////////	
//		
//		Pipe.addMsgIdx(pipe, ServerResponseSchema.MSG_TOCHANNEL_100);
//		Pipe.addLongValue(connectionId, pipe);
//		Pipe.addIntValue(sequenceNo, pipe);	
//		NetResponseWriter outputStream = (NetResponseWriter)Pipe.outputStream(pipe);
//	
//		outputStream.openField(hasContinuation? 0: HTTPFieldReader.END_OF_RESPONSE);
//		lastResponseWriterFinished = hasContinuation ? 0 : 1;		
//		
//		writable.write(outputStream); 
//		//this is not the end of the data so we must close this block
//		outputStream.write(RETURN_NEWLINE);
//		
//
//		if (1 == lastResponseWriterFinished) {			
//			//this is the end of the data, we must close the block
//			//and add the zero trailer
//			
//			//this adds 3, note the publishWithChunkPrefix also takes this into account
//			Appendables.appendHexDigitsRaw(outputStream, 0);
//			outputStream.write(AbstractAppendablePayloadResponseStage.RETURN_NEWLINE);
//						
//			//TODO: add trailing headers here. (no request for this feature yet)
//			
//			outputStream.write(AbstractAppendablePayloadResponseStage.RETURN_NEWLINE);
//		
//			
//		}
//
//		outputStream.publishWithChunkPrefix(block1HeaderBlobPosition, block1PositionOfLen);
//
//		return true;
//	}

	/**
	 *
	 * @param count int used as arg in TrafficOrderSchema.publishGo
	 * @param pipeIdx int used as arg in TrafficOrderSchema.publishGo
	 * @param gcc MsgCommandChannel arg used as arg in TrafficOrderSchema.publishGo
	 */
	public static void publishGo(int count, int pipeIdx, MsgCommandChannel<?> gcc) {				
		if (null != gcc.goPipe) { //no 'go' needed if pipe is null
			assert(pipeIdx>=0);
			TrafficOrderSchema.publishGo(gcc.goPipe, pipeIdx, count);
		}
	}

	/**
	 *
	 * @param durationNanos long arg used in PipeWriter.writeLong
	 * @param gcc MsgCommandChannel used in PipeWriter.presumeWriteFragment and .publishWrites
	 */
	public static void publishBlockChannel(long durationNanos, MsgCommandChannel<?> gcc) {
		
		if (null != gcc.goPipe) {
			PipeWriter.presumeWriteFragment(gcc.goPipe, TrafficOrderSchema.MSG_BLOCKCHANNEL_22);
			PipeWriter.writeLong(gcc.goPipe,TrafficOrderSchema.MSG_BLOCKCHANNEL_22_FIELD_DURATIONNANOS_13, durationNanos);
			PipeWriter.publishWrites(gcc.goPipe);
		} else {
			logger.info("Unable to use block channel for ns without an additonal feature use or USE_DELAY can be added.");
		}
	}

	/**
	 *
	 * @param timeMS long arg used in PipeWriter.writeLong
	 * @param gcc MsgCommandChannel used in PipeWriter.presumeWriteFragment and .publishWrites
	 */
	public static void publishBlockChannelUntil(long timeMS, MsgCommandChannel<?> gcc) {
		
		if (null != gcc.goPipe) {
			PipeWriter.presumeWriteFragment(gcc.goPipe, TrafficOrderSchema.MSG_BLOCKCHANNELUNTIL_23);
			PipeWriter.writeLong(gcc.goPipe,TrafficOrderSchema.MSG_BLOCKCHANNELUNTIL_23_FIELD_TIMEMS_14, timeMS);
			PipeWriter.publishWrites(gcc.goPipe);
		} else {
			logger.info("Unable to use block channel for ns without an additonal feature or USE_DELAY can be added.");
		}
	}
	
	public static void setPrivateTopics(
			MsgCommandChannel<?> cmd,
			PublishPrivateTopics publishPrivateTopics) {
		cmd.publishPrivateTopics = publishPrivateTopics;
	}

	public static boolean isGoPipe(MsgCommandChannel<?> cmd, Pipe<TrafficOrderSchema> target) {
		return (null==cmd.goPipe) || (target==cmd.goPipe);
	}



	
}