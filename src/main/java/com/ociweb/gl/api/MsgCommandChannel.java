package com.ociweb.gl.api;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.gl.impl.BuilderImpl;
import com.ociweb.gl.impl.schema.MessagePrivate;
import com.ociweb.gl.impl.schema.MessagePubSub;
import com.ociweb.gl.impl.schema.TrafficOrderSchema;
import com.ociweb.pronghorn.network.ServerCoordinator;
import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;
import com.ociweb.pronghorn.network.module.AbstractAppendablePayloadResponseStage;
import com.ociweb.pronghorn.network.schema.ClientHTTPRequestSchema;
import com.ociweb.pronghorn.network.schema.ServerResponseSchema;
import com.ociweb.pronghorn.pipe.DataOutputBlobWriter;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeConfig;
import com.ociweb.pronghorn.pipe.PipeConfigManager;
import com.ociweb.pronghorn.pipe.PipeWriter;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;
import com.ociweb.pronghorn.util.Appendables;
import com.ociweb.pronghorn.util.TrieParser;
import com.ociweb.pronghorn.util.TrieParserReader;
import com.ociweb.pronghorn.util.field.MessageConsumer;

/**
 * Represents a dedicated channel for communicating with a single device
 * or resource on an IoT system.
 */
public class MsgCommandChannel<B extends BuilderImpl> {

	private final Logger logger = LoggerFactory.getLogger(MsgCommandChannel.class);
	
    private Pipe<TrafficOrderSchema> goPipe;
    private Pipe<MessagePubSub> messagePubSub;
    private Pipe<ClientHTTPRequestSchema> httpRequest;
    private Pipe<ServerResponseSchema>[] netResponse;
    private Pipe<MessagePubSub>[] exclusivePubSub;
    
    private static final byte[] RETURN_NEWLINE = "\r\n".getBytes();
       
    private int lastResponseWriterFinished = 1;//starting in the "end" state
    
    private String[] exclusiveTopics =  new String[0];//TODO: make empty

    
    protected AtomicBoolean aBool = new AtomicBoolean(false);   

    protected static final long MS_TO_NS = 1_000_000;
         
    private Behavior listener;
    
    public static final int DYNAMIC_MESSAGING = 1<<0;
    public static final int STATE_MACHINE = DYNAMIC_MESSAGING;//state machine is based on DYNAMIC_MESSAGING;    
    
    public static final int NET_REQUESTER     = 1<<1;
    public static final int NET_RESPONDER     = 1<<2;
    public static final int ALL = DYNAMIC_MESSAGING | NET_REQUESTER | NET_RESPONDER;
      
    protected final B builder;

	public int maxHTTPContentLength;
	
	protected Pipe<?>[] optionalOutputPipes;
	public int initFeatures; //this can be modified up to the moment that we build the pipes.
	
	///////////
	///////////
	private String[] privateTopics = null;
	private Pipe<MessagePrivate>[] privateTopicPipes = null;
	private TrieParser privateTopicsTrie = null;
	private TrieParserReader privateTopicsTrieReader = null;
	protected PipeConfigManager pcm;
	//////////
    //////////
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
    }

    public void ensureDynamicMessaging() {
    	if (null!=this.goPipe) {
    		throw new UnsupportedOperationException("Too late, this method must be called in define behavior.");
    	}
    	this.initFeatures |= DYNAMIC_MESSAGING;
    }
    
    public void ensureDynamicMessaging(int queueLength, int maxMessageSize) {
    	if (null!=this.goPipe) {
    		throw new UnsupportedOperationException("Too late, this method must be called in define behavior.");
    	}
    	this.initFeatures |= DYNAMIC_MESSAGING;    
    	PipeConfig<MessagePubSub> config = pcm.getConfig(MessagePubSub.class);
		if (queueLength>config.minimumFragmentsOnPipe() || maxMessageSize>config.maxVarLenSize()) {
    		this.pcm.addConfig(queueLength, maxMessageSize, MessagePubSub.class);   
    	}
    }
    
    public void ensureHTTPClientRequesting() {
    	if (null!=this.goPipe) {
    		throw new UnsupportedOperationException("Too late, this method must be called in define behavior.");
    	}
    	this.initFeatures |= NET_REQUESTER;
    }
    
    public void ensureHTTPClientRequesting(int queueLength, int maxMessageSize) {
    	if (null!=this.goPipe) {
    		throw new UnsupportedOperationException("Too late, this method must be called in define behavior.");
    	}
    	this.initFeatures |= NET_REQUESTER;
    	PipeConfig<ClientHTTPRequestSchema> config = pcm.getConfig(ClientHTTPRequestSchema.class);
		if (queueLength>config.minimumFragmentsOnPipe() || maxMessageSize>config.maxVarLenSize()) {
    		this.pcm.addConfig(queueLength, maxMessageSize, ClientHTTPRequestSchema.class); 
    	}
    }
   
    public void ensureHTTPServerResponse() {
    	if (null!=this.goPipe) {
    		throw new UnsupportedOperationException("Too late, this method must be called in define behavior.");
    	}
    	this.initFeatures |= NET_RESPONDER;
    }
    
    public void ensureHTTPServerResponse(int queueLength, int maxMessageSize) {
    	if (null!=this.goPipe) {
    		throw new UnsupportedOperationException("Too late, this method must be called in define behavior.");
    	}
    	this.initFeatures |= NET_RESPONDER;    	
    	PipeConfig<ServerResponseSchema> config = pcm.getConfig(ServerResponseSchema.class);
		if (queueLength>config.minimumFragmentsOnPipe() || maxMessageSize>config.maxVarLenSize()) {
    		this.pcm.addConfig(queueLength, maxMessageSize, ServerResponseSchema.class);  
    	}
    }
    
    
    public void ensureCommandCountRoom(int queueLength) {
    	if (null!=this.goPipe) {
    		throw new UnsupportedOperationException("Too late, this method must be called in define behavior.");
    	}
    	PipeConfig<TrafficOrderSchema> goConfig = this.pcm.getConfig(TrafficOrderSchema.class);
    	if (queueLength>goConfig.minimumFragmentsOnPipe() ) {
    		this.pcm.addConfig(queueLength, 0, TrafficOrderSchema.class);
    	}
    }
    
    
	private void buildAllPipes() {
		   
		   if (null == this.goPipe) {
			   this.messagePubSub = ((this.initFeatures & DYNAMIC_MESSAGING) == 0) ? null : newPubSubPipe(pcm.getConfig(MessagePubSub.class), builder);
			   this.httpRequest   = ((this.initFeatures & NET_REQUESTER) == 0)     ? null : newNetRequestPipe(pcm.getConfig(ClientHTTPRequestSchema.class), builder);
			   //we always need a go pipe
			   this.goPipe = newGoPipe(pcm.getConfig(TrafficOrderSchema.class));
			   /////////////////////////
			   //build pipes for sending out the REST server responses
			   ////////////////////////       
			   Pipe<ServerResponseSchema>[] netResponse = null;
			   if ((this.initFeatures & NET_RESPONDER) != 0) {
				   //int parallelInstanceId = hardware.ac
				   if (-1 == parallelInstanceId) {
					   //we have only a single instance of this object so we must have 1 pipe for each parallel track
					   int p = builder.parallelism();
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
    
	protected boolean goHasRoom() {
		return PipeWriter.hasRoomForWrite(goPipe);
	}

    
    public Pipe<?>[] getOutputPipes() {
    	
    	//we wait till this last possible moment before building.
    	buildAllPipes();
    	 
    	 
    	int length = 0;
    	
    	if (null != messagePubSub) { //Index needed plust i2c index needed.
    		length++;
    	}
    	
    	if (null != httpRequest) {
    		length++;
    	}
    	
    	if (null != netResponse) {
    		length+=netResponse.length;
    	}
    	
    	if (null != goPipe) {//last
    		length++;
    	}
    	
    	length += exclusivePubSub.length;
    	
    	if (null!=optionalOutputPipes) {
    		length+=optionalOutputPipes.length;
    	}
    	
    	if (null!=privateTopicPipes) {
    		length+=privateTopicPipes.length;
    	}
  
    	int idx = 0;
    	Pipe[] results = new Pipe[length];
    	
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
    	
    	if (null!=privateTopicPipes) {
    		System.arraycopy(privateTopicPipes, 0, results, idx, privateTopicPipes.length);
    		idx+=privateTopicPipes.length;
    	}
    	
    	if (null != goPipe) {//last
    		results[idx++] = goPipe;
    	}
    	
    	return results;
    }
    
    
    private static <B extends BuilderImpl> Pipe<MessagePubSub> newPubSubPipe(PipeConfig<MessagePubSub> config, B builder) {
    	return new Pipe<MessagePubSub>(config) {
			@SuppressWarnings("unchecked")
			@Override
			protected DataOutputBlobWriter<MessagePubSub> createNewBlobWriter() {
				return new PubSubWriter(this);
			}    		
    	};
    }
    
    private static <B extends BuilderImpl> Pipe<ClientHTTPRequestSchema> newNetRequestPipe(PipeConfig<ClientHTTPRequestSchema> config, B builder) {

    	return new Pipe<ClientHTTPRequestSchema>(config) {
			@SuppressWarnings("unchecked")
			@Override
			protected DataOutputBlobWriter<ClientHTTPRequestSchema> createNewBlobWriter() {
				return new PayloadWriter<ClientHTTPRequestSchema>(this);
			}    		
    	};
    }


	private Pipe<TrafficOrderSchema> newGoPipe(PipeConfig<TrafficOrderSchema> goPipeConfig) {
		return new Pipe<TrafficOrderSchema>(goPipeConfig);
	}
        
    
    
    public static void setListener(MsgCommandChannel c, Behavior listener) {
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


    /**
     * Causes this channel to delay processing any actions until the specified
     * amount of time has elapsed.
     *
     * @param durationNanos Nanos to delay
     *
     * @return True if blocking was successful, and false otherwise.
     */
    public boolean block(long durationNanos) {
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
    public boolean blockUntil(long msTime) {
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
    
    /**
     * Submits an HTTP GET request asynchronously.
     *
     * The response to this HTTP GET will be sent to any HTTPResponseListeners
     * associated with the listener for this command channel.
     *
     * @param domain Root domain to submit the request to (e.g., google.com)
     * @param port Port to submit the request to.
     * @param path Route on the domain to submit the request to (e.g., /api/hello)
     *
     * @return True if the request was successfully submitted, and false otherwise.
     */
    public boolean httpGet(CharSequence domain, int port, CharSequence path) {
    	return httpGet(domain,port,path,(HTTPResponseListener)listener);

    }

    /**
     * Submits an HTTP GET request asynchronously.
     *
     * @param host Root domain to submit the request to (e.g., google.com)
     * @param port Port to submit the request to.
     * @param route Route on the domain to submit the request to (e.g., /api/hello)
     * @param listener {@link HTTPResponseListener} that will handle the response.
     *
     * @return True if the request was successfully submitted, and false otherwise.
     */
    private boolean httpGet(CharSequence host, int port, CharSequence route, HTTPResponseListener listener) {
    	return httpGet(host, port, route, builder.behaviorId((HTTPResponseListener)listener)); 	
    }
    
    public boolean httpGet(CharSequence host, CharSequence route) {    	
    	return httpGet(host, builder.isClientTLS()?443:80, route, (HTTPResponseListener)listener);
    }
    public boolean httpGet(CharSequence host, CharSequence route, int behaviorId) {
    	return httpGet(host, builder.isClientTLS()?443:80, route, behaviorId);
    }
	public boolean httpGet(CharSequence host, int port, CharSequence route, int behaviorId) {
		assert(builder.isUseNetClient());
		assert((this.initFeatures & NET_REQUESTER)!=0) : "must turn on NET_REQUESTER to use this method";
		
		if (PipeWriter.hasRoomForWrite(goPipe) && PipeWriter.tryWriteFragment(httpRequest, ClientHTTPRequestSchema.MSG_HTTPGET_100)) {
                	    
    		PipeWriter.writeInt(httpRequest, ClientHTTPRequestSchema.MSG_HTTPGET_100_FIELD_PORT_1, port);
    		PipeWriter.writeUTF8(httpRequest, ClientHTTPRequestSchema.MSG_HTTPGET_100_FIELD_HOST_2, host);
    		PipeWriter.writeUTF8(httpRequest, ClientHTTPRequestSchema.MSG_HTTPGET_100_FIELD_PATH_3, route);
			PipeWriter.writeInt(httpRequest, ClientHTTPRequestSchema.MSG_HTTPGET_100_FIELD_LISTENER_10, behaviorId);
    		PipeWriter.writeUTF8(httpRequest, ClientHTTPRequestSchema.MSG_HTTPGET_100_FIELD_HEADERS_7, "");
    		    		
    		PipeWriter.publishWrites(httpRequest);
                		
    		publishGo(1, builder.netIndex(), this);
    		    	            
            return true;
        }        
        return false;
	}

    /**
     * Submits an HTTP POST request asynchronously.
     *
     * The response to this HTTP POST will be sent to any HTTPResponseListeners
     * associated with the listener for this command channel.
     *
     * @param domain Root domain to submit the request to (e.g., google.com)
     * @param port Port to submit the request to.
     * @param route Route on the domain to submit the request to (e.g., /api/hello)
     *
     * @return True if the request was successfully submitted, and false otherwise.
     */
    public PayloadWriter httpPost(CharSequence domain, int port, CharSequence route) {
    	return httpPost(domain,port,route,(HTTPResponseListener)listener);    	
    }

    /**
     * Submits an HTTP POST request asynchronously.
     *
     * @param host Root domain to submit the request to (e.g., google.com)
     * @param port Port to submit the request to.
     * @param route Route on the domain to submit the request to (e.g., /api/hello)
     * @param listener {@link HTTPResponseListener} that will handle the response.
     *
     * @return True if the request was successfully submitted, and false otherwise.
     */
    public PayloadWriter<ClientHTTPRequestSchema> httpPost(CharSequence host, int port, CharSequence route, HTTPResponseListener listener) {
    	
    	assert((this.initFeatures & NET_REQUESTER)!=0) : "must turn on NET_REQUESTER to use this method";
    	
    	if (PipeWriter.hasRoomForWrite(goPipe) && PipeWriter.tryWriteFragment(httpRequest, ClientHTTPRequestSchema.MSG_HTTPPOST_101)) {
                	    
    		PipeWriter.writeInt(httpRequest, ClientHTTPRequestSchema.MSG_HTTPPOST_101_FIELD_PORT_1, port);
    		PipeWriter.writeUTF8(httpRequest, ClientHTTPRequestSchema.MSG_HTTPPOST_101_FIELD_HOST_2, host);
    		PipeWriter.writeUTF8(httpRequest, ClientHTTPRequestSchema.MSG_HTTPPOST_101_FIELD_PATH_3, route);
    		PipeWriter.writeInt(httpRequest, ClientHTTPRequestSchema.MSG_HTTPPOST_101_FIELD_LISTENER_10, System.identityHashCode(listener));

    		publishGo(1, builder.netIndex(), this);
            
            PayloadWriter<ClientHTTPRequestSchema> pw = (PayloadWriter<ClientHTTPRequestSchema>) Pipe.outputStream(httpRequest);
           
            pw.openField(ClientHTTPRequestSchema.MSG_HTTPPOST_101_FIELD_PAYLOAD_5, this);  
            return pw;
        } else {
        	return null;
        }    	
    }

    /**
     * Subscribes the listener associated with this command channel to
     * a topic.
     *
     * @param topic Topic to subscribe to.
     *
     * @return True if the topic was successfully subscribed to, and false
     *         otherwise.
     */
    public boolean subscribe(CharSequence topic) {
    			
		assert((0 != (initFeatures & DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
    	
    	if (null==listener || null == goPipe) {
    		throw new UnsupportedOperationException("Can not subscribe before startup. Call addSubscription when registering listener."); 
    	}
    	
        return subscribe(topic, (PubSubListener)listener);
    }

    /**
     * Subscribes a listener to a topic on this command channel.
     *
     * @param topic Topic to subscribe to.
     * @param listener Listener to subscribe.
     *
     * @return True if the topic was successfully subscribed to, and false
     *         otherwise.
     */
    public boolean subscribe(CharSequence topic, PubSubListener listener) {
		assert((0 != (initFeatures & DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";

    	assert(null!=goPipe) : "must turn on Dynamic Messaging for this channel";
        if (PipeWriter.hasRoomForWrite(goPipe) && PipeWriter.tryWriteFragment(messagePubSub, MessagePubSub.MSG_SUBSCRIBE_100)) {
            
            PipeWriter.writeInt(messagePubSub, MessagePubSub.MSG_SUBSCRIBE_100_FIELD_SUBSCRIBERIDENTITYHASH_4, System.identityHashCode(listener));
            PipeWriter.writeUTF8(messagePubSub, MessagePubSub.MSG_SUBSCRIBE_100_FIELD_TOPIC_1, topic);
            
            PipeWriter.publishWrites(messagePubSub);
            
            builder.releasePubSubTraffic(1, this);
            
            return true;
        }        
        return false;
    }

    /**
     * Unsubscribes the listener associated with this command channel from
     * a topic.
     *
     * @param topic Topic to unsubscribe from.
     *
     * @return True if the topic was successfully unsubscribed from, and false otherwise.
     */
    public boolean unsubscribe(CharSequence topic) {
		assert((0 != (initFeatures & DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";

        return unsubscribe(topic, (PubSubListener)listener);
    }

    /**
     * Unsubscribes a listener from a topic.
     *
     * @param topic Topic to unsubscribe from.
     * @param listener Listener to unsubscribe.
     *
     * @return True if the topic was successfully unsubscribed from, and false otherwise.
     */
    public boolean unsubscribe(CharSequence topic, PubSubListener listener) {
		 assert((0 != (initFeatures & DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";

        if (PipeWriter.hasRoomForWrite(goPipe) && PipeWriter.tryWriteFragment(messagePubSub, MessagePubSub.MSG_UNSUBSCRIBE_101)) {
            
            PipeWriter.writeInt(messagePubSub, MessagePubSub.MSG_SUBSCRIBE_100_FIELD_SUBSCRIBERIDENTITYHASH_4, System.identityHashCode(listener));
            PipeWriter.writeUTF8(messagePubSub, MessagePubSub.MSG_UNSUBSCRIBE_101_FIELD_TOPIC_1, topic);
            
            PipeWriter.publishWrites(messagePubSub);
            
            builder.releasePubSubTraffic(1, this);
            
            return true;
        }        
        return false;
    }

    /**
     * Changes the state of this command channel's state machine.
     *
     * @param state State to transition to.
     *
     * @return True if the state was successfully transitioned, and false otherwise.
     */
    public <E extends Enum<E>> boolean changeStateTo(E state) {
		 assert((0 != (initFeatures & DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";

    	 assert(builder.isValidState(state));
    	 if (!builder.isValidState(state)) {
    		 throw new UnsupportedOperationException("no match "+state.getClass());
    	 }
    	
    	 if (PipeWriter.hasRoomForWrite(goPipe) && PipeWriter.tryWriteFragment(messagePubSub, MessagePubSub.MSG_CHANGESTATE_70)) {

    		 PipeWriter.writeInt(messagePubSub, MessagePubSub.MSG_CHANGESTATE_70_FIELD_ORDINAL_7,  state.ordinal());
             PipeWriter.publishWrites(messagePubSub);
             
             builder.releasePubSubTraffic(1, this);
    		 return true;
    	 }

    	return false;
    	
    }

    public void presumePublishTopic(CharSequence topic, PubSubWritable writable) {
    	presumePublishTopic(topic, writable, WaitFor.All);
    }
    
    public void presumePublishTopic(CharSequence topic, PubSubWritable writable, WaitFor ap) {
    	assert((0 != (initFeatures & DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
    	
    	if (publishTopic(topic, writable, ap)) {
			return;
		} else { 
			logger.warn("unable to publish on topic {} must wait.",topic);
			while (!publishTopic(topic, writable, ap)) {
				Thread.yield();
			}
		}
    }
    
    
    public boolean publishTopic(CharSequence topic, PubSubWritable writable) {
    	return publishTopic(topic, writable, WaitFor.All);
    }
    /**
     * Opens a topic on this channel for writing.
     *
     * @param topic Topic to open.
     *
     * @return {@link PayloadWriter} attached to the given topic.
     */
    public boolean publishTopic(CharSequence topic, PubSubWritable writable, WaitFor ap) {
		assert((0 != (initFeatures & DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
        assert(writable != null);
        assert(isNotPrivate(topic)) : "private topics may not be selected by CharSequence.";
        
        if (PipeWriter.hasRoomForWrite(goPipe) && 
        	PipeWriter.tryWriteFragment(messagePubSub, MessagePubSub.MSG_PUBLISH_103)) {
    		
    		PipeWriter.writeInt(messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_QOS_5, ap.policy());
        	PipeWriter.writeUTF8(messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1, topic);         
        	
            PubSubWriter pw = (PubSubWriter) Pipe.outputStream(messagePubSub);
            
            pw.openField(MessagePubSub.MSG_PUBLISH_103_FIELD_PAYLOAD_3,this);
            writable.write(pw);                   
            pw.publish();
            publishGo(1,builder.pubSubIndex(), this);
                        
            return true;
            
        } else {
            return false;
        }
    }

    public boolean publishTopic(CharSequence topic) {
    	return publishTopic(topic, WaitFor.All);
    }
    
    public boolean publishTopic(CharSequence topic, WaitFor ap) {
		assert((0 != (initFeatures & DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
        assert(isNotPrivate(topic)) : "private topics may not be selected by CharSequence.";
        
        if (PipeWriter.hasRoomForWrite(goPipe) && 
        	PipeWriter.tryWriteFragment(messagePubSub, MessagePubSub.MSG_PUBLISH_103)) {
    		
    		PipeWriter.writeInt(messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_QOS_5, ap.policy());
        	PipeWriter.writeUTF8(messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1, topic);         
        	
            PubSubWriter pw = (PubSubWriter) Pipe.outputStream(messagePubSub);
            
            pw.openField(MessagePubSub.MSG_PUBLISH_103_FIELD_PAYLOAD_3,this);            
            pw.publish();
            publishGo(1,builder.pubSubIndex(), this);
                        
            return true;
            
        } else {
            return false;
        }
    }
    
    //TODO: add privateTopic support
    
//    public boolean publishTopic(byte[] topic, PubSubWritable writable) {
//		assert((0 != (initFeatures & DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
//        assert(writable != null);
//        
//        int token = (int)privateTopicsTrieReader.query(privateTopicsTrieReader,
//        		                                   privateTopicsTrie, 
//        		                                   topic, 0, topic.length, Integer.MAX_VALUE);
//        
//        if (token>=0) {
//        	//this is a private topic
//            
//			Pipe<MessagePrivate> output = privateTopicPipes[token];
//			if (PipeWriter.tryWriteFragment(output, MessagePrivate.MSG_PUBLISH_1)) {
//			
////			PubSubWriter pw = (PubSubWriter) Pipe.outputStream(output);
////            pw.openField(MessagePrivate.MSG_PUBLISH_1_FIELD_PAYLOAD_3,this);
////            writable.write(pw);//TODO: cool feature, writable to return false to abandon write..
////            pw.publish();
//		        	
//				throw new UnsupportedOperationException();
//        	
//        		//return true;
//			} else {
//				return false;
//			}
//        } else {
//        	//should not be called when	DYNAMIC_MESSAGING is not on.
//        	
//	        //this is a public topic
//        	if (PipeWriter.hasRoomForWrite(goPipe) && 
//	        	PipeWriter.tryWriteFragment(messagePubSub, MessagePubSub.MSG_PUBLISH_103)) {
//	            
//	        	PipeWriter.writeBytes(messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1, topic);         
//	        	
//	            PubSubWriter pw = (PubSubWriter) Pipe.outputStream(messagePubSub);
//	            
//	            pw.openField(MessagePubSub.MSG_PUBLISH_103_FIELD_PAYLOAD_3,this);
//	            writable.write(pw);//TODO: cool feature, writable to return false to abandon write..
//	                        
//	            pw.publish();
//	            publishGo(1,builder.pubSubIndex(), this);
//	                        
//	            return true;
//	            
//	        } else {
//	            return false;
//	        }
//        }
//    }
    
    public void presumePublishTopic(TopicWritable topic, PubSubWritable writable) {
    	presumePublishTopic(topic,writable,WaitFor.All);
    }        
    public void presumePublishTopic(TopicWritable topic, PubSubWritable writable, WaitFor ap) {
    	assert((0 != (initFeatures & DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";

    	if (publishTopic(topic, writable, ap)) {
			return;
		} else { 
			logger.warn("unable to publish on topic {} must wait.",topic);
			while (!publishTopic(topic, writable, ap)) {
				Thread.yield();
			}
		}
    }
    
    public boolean publishTopic(TopicWritable topic, PubSubWritable writable) {
    	return publishTopic(topic, writable, WaitFor.All);    	
    }
    
    public boolean publishTopic(TopicWritable topic, PubSubWritable writable, WaitFor ap) {
		assert((0 != (initFeatures & DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
        assert(writable != null);
        assert(isNotPrivate(topic)) : "private topics may not be dynamicaly constructed.";
                
        if (PipeWriter.hasRoomForWrite(goPipe) && 
        	PipeWriter.tryWriteFragment(messagePubSub, MessagePubSub.MSG_PUBLISH_103)) {
        	
    		
    		PipeWriter.writeInt(messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_QOS_5, ap.policy());
    		
        	PubSubWriter pw = (PubSubWriter) Pipe.outputStream(messagePubSub);
        	
        	pw.openField(MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1,this);
        	topic.write(pw);
        	pw.closeHighLevelField(MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1);
           
            pw.openField(MessagePubSub.MSG_PUBLISH_103_FIELD_PAYLOAD_3,this);
            writable.write(pw);
                        
            pw.publish();
            publishGo(1,builder.pubSubIndex(), this);
                        
            return true;
            
        } else {
            return false;
        }
    }
    
    
    public boolean publishTopic(TopicWritable topic) {
    	return publishTopic(topic, WaitFor.All);
    }    
    public boolean publishTopic(TopicWritable topic, WaitFor ap) {
		assert((0 != (initFeatures & DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
        assert(isNotPrivate(topic)) : "private topics may not be dynamicaly constructed.";
                
        if (PipeWriter.hasRoomForWrite(goPipe) && 
        	PipeWriter.tryWriteFragment(messagePubSub, MessagePubSub.MSG_PUBLISH_103)) {
        	    		
    		PipeWriter.writeInt(messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_QOS_5, ap.policy());
        	
        	PubSubWriter pw = (PubSubWriter) Pipe.outputStream(messagePubSub);
        	
        	pw.openField(MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1,this);
        	topic.write(pw);
        	pw.closeHighLevelField(MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1);
           
            pw.openField(MessagePubSub.MSG_PUBLISH_103_FIELD_PAYLOAD_3,this);     
            pw.publish();
            publishGo(1,builder.pubSubIndex(), this);
                        
            return true;
            
        } else {
            return false;
        }
    }
    
	private boolean isNotPrivate(TopicWritable topic) {
		StringBuilder target = new StringBuilder();
		topic.write(target);
		String topicString = target.toString();
		return isNotPrivate(topicString);
	}

	private boolean isNotPrivate(CharSequence topicString) {
		if (null == privateTopics) {
			return true;
		}
		int i = privateTopics.length;
		while (--i>=0) {
			if (topicString.equals(privateTopics[i])) {
				return false;
			}
		}
		return true;
	}

	public void presumePublishStructuredTopic(CharSequence topic, PubSubStructuredWritable writable) {
		assert((0 != (initFeatures & DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";

		if (publishStructuredTopic(topic, writable)) {
			return;
		} else { 
			logger.warn("unable to publish on topic {} must wait.",topic);
			while (!publishStructuredTopic(topic, writable)) {
				Thread.yield();
			}
		}
    }
        
	public boolean copyStructuredTopic(CharSequence topic, 
            MessageReader reader, 
            MessageConsumer consumer) {
		return copyStructuredTopic(topic, reader, consumer, WaitFor.All);
	}
	
    public boolean copyStructuredTopic(CharSequence topic, 
    		                           MessageReader reader, 
    		                           MessageConsumer consumer,
    		                           WaitFor ap) {
    	assert((0 != (initFeatures & DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
    	
    	int pos = reader.absolutePosition();    	
    	if (consumer.process(reader) 
    		&& PipeWriter.hasRoomForWrite(goPipe) 
        	&& PipeWriter.tryWriteFragment(messagePubSub, MessagePubSub.MSG_PUBLISH_103)  ) {
    		
    		PipeWriter.writeInt(messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_QOS_5, ap.policy());
    		
    		PipeWriter.writeUTF8(messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1, topic);            
        	
            PubSubWriter pw = (PubSubWriter) Pipe.outputStream(messagePubSub);
            pw.openField(MessagePubSub.MSG_PUBLISH_103_FIELD_PAYLOAD_3,this); 
            
            reader.absolutePosition(pos);//restore position as unread
            //direct copy from one to the next
            reader.readInto(pw, reader.available());

            pw.publish();
           	publishGo(1,builder.pubSubIndex(), this);  		
    		
    		return true;
    	} else {
    		reader.absolutePosition(pos);//restore position as unread
    		return false;
    	}
    }
    
    public boolean publishStructuredTopic(CharSequence topic, PubSubStructuredWritable writable) {
    	return publishStructuredTopic(topic, writable, WaitFor.All);
    }
    
    public boolean publishStructuredTopic(CharSequence topic, PubSubStructuredWritable writable, WaitFor ap) {
 	    assert((0 != (initFeatures & DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
     	assert(writable != null);
        assert(null != goPipe);
        assert(null != messagePubSub);
        if (PipeWriter.hasRoomForWrite(goPipe) 
        	&& PipeWriter.tryWriteFragment(messagePubSub, MessagePubSub.MSG_PUBLISH_103)) {
    		
    		PipeWriter.writeInt(messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_QOS_5, ap.policy());
        	PipeWriter.writeUTF8(messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1, topic);            
                    	
            PubSubWriter pw = (PubSubWriter) Pipe.outputStream(messagePubSub);
            pw.openField(MessagePubSub.MSG_PUBLISH_103_FIELD_PAYLOAD_3,this);            
            writable.write(pw); //TODO: cool feature, writable to return false to abandon write.. 
            
            pw.publish();
           	publishGo(1,builder.pubSubIndex(), this);
           
    
            return true;
            
        } else {
            return false;
        }
    }

	public boolean publishHTTPResponse(HTTPFieldReader w, int statusCode) {
		
		 assert((0 != (initFeatures & NET_RESPONDER))) : "CommandChannel must be created with NET_RESPONDER flag";

		//logger.info("Building response for connection {} sequence {} ",w.getConnectionId(),w.getSequenceCode());
		
		return publishHTTPResponse(w.getConnectionId(), w.getSequenceCode(),
				statusCode,
				HTTPFieldReader.END_OF_RESPONSE | HTTPFieldReader.CLOSE_CONNECTION,
				null,
				NetWritable.NO_OP); //no type and no body so use null
	}

	public boolean publishHTTPResponse(HTTPFieldReader w, 
										            int statusCode, final int context, 
										            HTTPContentTypeDefaults contentType,
										            NetWritable writable) {
		
		 assert((0 != (initFeatures & NET_RESPONDER))) : "CommandChannel must be created with NET_RESPONDER flag";

		return publishHTTPResponse(w.getConnectionId(), w.getSequenceCode(),
				                statusCode, context, contentType, writable);
	}	

	///////////////////////////////////
	//these fields are needed for holding the position data for the first block of two
	//this is required so we can go back to fill in length after the second block
	//length is known
	private long block1PositionOfLen;
	private int block1HeaderBlobPosition;
	//this is not thread safe but works because command channels are only used by same thread
	////////////////////////////////////
	
	public boolean publishHTTPResponse(long connectionId, long sequenceCode, 
			                                            int statusCode, final int context, 
			                                            HTTPContentTypeDefaults contentType,
			                                            NetWritable writable) {
		
		assert((0 != (initFeatures & NET_RESPONDER))) : "CommandChannel must be created with NET_RESPONDER flag";

		final int sequenceNo = 0xFFFFFFFF & (int)sequenceCode;
		final int parallelIndex = 0xFFFFFFFF & (int)(sequenceCode>>32);
	
		assert(1==lastResponseWriterFinished) : "Previous write was not ended can not start another.";
			
		Pipe<ServerResponseSchema> pipe = netResponse.length>1 ? netResponse[parallelIndex] : netResponse[0];
		
		//logger.trace("try new publishHTTPResponse");
		if (!Pipe.hasRoomForWrite(pipe)) {
			return false;
		}		

		///////////////////////////////////////
		//message 1 which contains the headers
		//////////////////////////////////////		
		holdEmptyBlock(connectionId, sequenceNo, pipe);
				
		//////////////////////////////////////////
		//begin message 2 which contains the body
		//////////////////////////////////////////
		NetResponseWriter outputStream = (NetResponseWriter)Pipe.outputStream(pipe);	
		
		Pipe.addMsgIdx(pipe, ServerResponseSchema.MSG_TOCHANNEL_100);
		Pipe.addLongValue(connectionId, pipe);
		Pipe.addIntValue(sequenceNo, pipe);	
		
		outputStream = (NetResponseWriter)Pipe.outputStream(pipe);
		
		lastResponseWriterFinished = 1&(context>>ServerCoordinator.END_RESPONSE_SHIFT);

		//NB: context passed in here is looked at to know if this is END_RESPONSE and if so
		//then the length is added if not then the header will designate chunked.
		outputStream.openField(statusCode, context, contentType);
		writable.write(outputStream); 
		
		if (0 == lastResponseWriterFinished) {
			// for chunking we must end this block			
			outputStream.write(RETURN_NEWLINE);
		}
		
		outputStream.publishWithHeader(block1HeaderBlobPosition, block1PositionOfLen); //closeLowLevelField and publish 
	
		return true;

	}

	private void holdEmptyBlock(long connectionId, final int sequenceNo, Pipe<ServerResponseSchema> pipe) {
	
			Pipe.addMsgIdx(pipe, ServerResponseSchema.MSG_TOCHANNEL_100);
			Pipe.addLongValue(connectionId, pipe);
			Pipe.addIntValue(sequenceNo, pipe);	
			
			NetResponseWriter outputStream = (NetResponseWriter)Pipe.outputStream(pipe);	
			block1HeaderBlobPosition = Pipe.getWorkingBlobHeadPosition(pipe);
	
			DataOutputBlobWriter.openFieldAtPosition(outputStream, block1HeaderBlobPosition); 	//no context, that will come in the second message 
	        
			//for the var field we store this as meta then length
			block1PositionOfLen = 1+Pipe.workingHeadPosition(pipe);
			
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
	
	public boolean publishHTTPResponseContinuation(HTTPFieldReader w, 
										int context, NetWritable writable) {
		return publishHTTPResponseContinuation(w.getConnectionId(),w.getSequenceCode(), context, writable);
	}

	public boolean publishHTTPResponseContinuation(long connectionId, long sequenceCode, 
			                                       int context, NetWritable writable) {
		
    	assert((0 != (initFeatures & NET_RESPONDER))) : "CommandChannel must be created with NET_RESPONDER flag";

		final int sequenceNo = 0xFFFFFFFF & (int)sequenceCode;
		final int parallelIndex = 0xFFFFFFFF & (int)(sequenceCode>>32);		
		
		assert(0==lastResponseWriterFinished) : "Unable to find write in progress, nothing to continue with";
		
		Pipe<ServerResponseSchema> pipe = netResponse.length>1 ? netResponse[parallelIndex] : netResponse[0];

		//logger.trace("calling publishHTTPResponseContinuation");
		
		if (!Pipe.hasRoomForWrite(pipe)) {
			return false;
		}
		
		
		///////////////////////////////////////
		//message 1 which contains the chunk length
		//////////////////////////////////////		
		holdEmptyBlock(connectionId, sequenceNo, pipe);
		
		///////////////////////////////////////
		//message 2 which contains the chunk
		//////////////////////////////////////	
		
		Pipe.addMsgIdx(pipe, ServerResponseSchema.MSG_TOCHANNEL_100);
		Pipe.addLongValue(connectionId, pipe);
		Pipe.addIntValue(sequenceNo, pipe);	
		NetResponseWriter outputStream = (NetResponseWriter)Pipe.outputStream(pipe);
	
		outputStream.openField(context);
		
		writable.write(outputStream); 
		//this is not the end of the data so we must close this block
		outputStream.write(RETURN_NEWLINE);
		
		lastResponseWriterFinished = 1&(context>>ServerCoordinator.END_RESPONSE_SHIFT);		

		if (1 == lastResponseWriterFinished) {			
			//this is the end of the data, we must close the block
			//and add the zero trailer
			
			//this adds 3, note the publishWithChunkPrefix also takes this into account
			Appendables.appendHexDigitsRaw(outputStream, 0);
			outputStream.write(AbstractAppendablePayloadResponseStage.RETURN_NEWLINE);
						
			//TODO: add trailing headers here. (no request for this feature yet)
			
			outputStream.write(AbstractAppendablePayloadResponseStage.RETURN_NEWLINE);
		
			
		}

		outputStream.publishWithChunkPrefix(block1HeaderBlobPosition, block1PositionOfLen);

		return true;
	}

	public static void publishGo(int count, int pipeIdx, MsgCommandChannel<?> gcc) {				
		assert(pipeIdx>=0);
		TrafficOrderSchema.publishGo(gcc.goPipe, pipeIdx, count);    
	}
	
	public static void publishBlockChannel(long durationNanos, MsgCommandChannel<?> gcc) {
		TrafficOrderSchema.publishBlockChannel(gcc.goPipe, durationNanos);
	}

	public static void publishBlockChannelUntil(long timeMS, MsgCommandChannel<?> gcc) {
		TrafficOrderSchema.publishBlockChannelUntil(gcc.goPipe, timeMS);
	}
	
	public String[] privateTopics() {
		return privateTopics;
	}
	
	public void privateTopics(String... topic) {
		privateTopics = topic;
		///
		int i = topic.length;
		privateTopicPipes = new Pipe[i];
		PipeConfig<MessagePrivate> config = pcm.getConfig(MessagePrivate.class);
		while (--i >= 0) {
			 privateTopicPipes[i] = new Pipe(config);
		}
		///
		int j= topic.length;
		privateTopicsTrie = new TrieParser(j,1,false,false,false);//a topic is case-sensitive
		while (--j>=0) {
			privateTopicsTrie.setUTF8Value(topic[j], j);//to matching pipe index
		}
		privateTopicsTrieReader = new TrieParserReader(0,true);
	}

	public boolean isGoPipe(Pipe<TrafficOrderSchema> target) {
		return target==goPipe;
	}
	
	
}