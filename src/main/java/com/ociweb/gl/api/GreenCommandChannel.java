package com.ociweb.gl.api;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.gl.impl.BuilderImpl;
import com.ociweb.gl.impl.schema.MessagePubSub;
import com.ociweb.gl.impl.schema.TrafficOrderSchema;
import com.ociweb.pronghorn.network.ServerCoordinator;
import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;
import com.ociweb.pronghorn.network.schema.ClientHTTPRequestSchema;
import com.ociweb.pronghorn.network.schema.ServerResponseSchema;
import com.ociweb.pronghorn.pipe.DataOutputBlobWriter;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeConfig;
import com.ociweb.pronghorn.pipe.PipeConfigManager;
import com.ociweb.pronghorn.pipe.PipeWriter;
import com.ociweb.pronghorn.pipe.RawDataSchema;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;

/**
 * Represents a dedicated channel for communicating with a single device
 * or resource on an IoT system.
 */
public class GreenCommandChannel<B extends BuilderImpl> {

	private final Logger logger = LoggerFactory.getLogger(GreenCommandChannel.class);
	
    private final Pipe<TrafficOrderSchema> goPipe;
    private final Pipe<MessagePubSub> messagePubSub;
    private final Pipe<ClientHTTPRequestSchema> httpRequest;
    
    private final Pipe<ServerResponseSchema>[] netResponse;
    
    private int lastResponseWriterFinished = 1;//starting in the "end" state
    
    private final Pipe<MessagePubSub>[] exclusivePubSub;
    private String[] exclusiveTopics =  new String[0];//TODO: make empty

    
    protected AtomicBoolean aBool = new AtomicBoolean(false);   

    protected static final long MS_TO_NS = 1_000_000;
         
    private Object listener;
    
    public static final int DYNAMIC_MESSAGING = 1<<0;
    public static final int STATE_MACHINE = DYNAMIC_MESSAGING;//state machine is based on DYNAMIC_MESSAGING;    
    
    public static final int NET_REQUESTER     = 1<<1;
    public static final int NET_RESPONDER     = 1<<2;
    public static final int ALL = DYNAMIC_MESSAGING | NET_REQUESTER | NET_RESPONDER;
      
    protected B builder;

    private final static int[] EMPTY = new int[0];
    private final static Pipe[] EMPTY_PIPES = new Pipe[0];

    private final int MAX_TEXT_LENGTH = 64;
    private final Pipe<RawDataSchema> digitBuffer = new Pipe<RawDataSchema>(new PipeConfig<RawDataSchema>(RawDataSchema.instance,3,MAX_TEXT_LENGTH));
    
	public final int maxHTTPContentLength;
	
	protected Pipe<?>[] optionalOutputPipes;
	protected final int initFeatures;
    
    public GreenCommandChannel(GraphManager gm, B hardware,
			      		    int parallelInstanceId,
			      		    PipeConfigManager pcm
				           ) {
    	this(gm,hardware,ALL, parallelInstanceId, pcm);
    }
    
    public GreenCommandChannel(GraphManager gm, B builder,
    					  int features,
    					  int parallelInstanceId,
    					  PipeConfigManager pcm
    		             ) {
    
       this.initFeatures = features;//this is held so we can check at every method call that its configured right
                             
       this.messagePubSub = ((features & DYNAMIC_MESSAGING) == 0) ? null : newPubSubPipe(pcm.getConfig(MessagePubSub.class));
       this.httpRequest   = ((features & NET_REQUESTER) == 0)     ? null : newNetRequestPipe(pcm.getConfig(ClientHTTPRequestSchema.class));

       this.digitBuffer.initBuffers();
       
       /////////////////////////
       //build pipes for sending out the REST server responses
       ////////////////////////       
       Pipe<ServerResponseSchema>[] netResponse = null;
       if ((features & NET_RESPONDER) != 0) {
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
       
       if (null != this.messagePubSub) {
    	   if (!Pipe.isInit(messagePubSub)) {
    		   messagePubSub.initBuffers();
    	   }
       }

       //we always need a go pipe
       this.goPipe = newGoPipe(pcm.getConfig(TrafficOrderSchema.class));
       
       ///////////////////

       int e = this.exclusiveTopics.length;
       this.exclusivePubSub = (Pipe<MessagePubSub>[])new Pipe[e];
       while (--e>=0) {
    	   exclusivePubSub[e] = newPubSubPipe(pcm.getConfig(MessagePubSub.class));
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
	   
       this.builder = builder;
       
    }

    
	protected boolean goHasRoom() {
		return PipeWriter.hasRoomForWrite(goPipe);
	}

    
    public Pipe<?>[] getOutputPipes() {
    	
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
    	
    	if (null != goPipe) {//last
    		results[idx++] = goPipe;
    	}
    	
    	return results;
    }
    
    
    private static Pipe<MessagePubSub> newPubSubPipe(PipeConfig<MessagePubSub> config) {
    	return new Pipe<MessagePubSub>(config) {
			@SuppressWarnings("unchecked")
			@Override
			protected DataOutputBlobWriter<MessagePubSub> createNewBlobWriter() {
				return new PubSubWriter(this);
			}    		
    	};
    }
    
    private static Pipe<ClientHTTPRequestSchema> newNetRequestPipe(PipeConfig<ClientHTTPRequestSchema> config) {

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
        
    
    
    public static void setListener(GreenCommandChannel c, Object listener) {
        if (null != c.listener) {
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
     * Submits an HTTP GET request asynchronously.
     *
     * The response to this HTTP GET will be sent to any HTTPResponseListeners
     * associated with the listener for this command channel.
     *
     * @param domain Root domain to submit the request to (e.g., google.com)
     * @param port Port to submit the request to.
     * @param route Route on the domain to submit the request to (e.g., /api/hello)
     *
     * @return True if the request was successfully submitted, and false otherwise.
     */
    public boolean httpGet(CharSequence domain, int port, CharSequence route) {
    	return httpGet(domain,port,route,(HTTPResponseListener)listener);

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
    public boolean httpGet(CharSequence host, int port, CharSequence route, HTTPResponseListener listener) {
    	
    	if (PipeWriter.hasRoomForWrite(goPipe) && PipeWriter.tryWriteFragment(httpRequest, ClientHTTPRequestSchema.MSG_HTTPGET_100)) {
                	    
    		PipeWriter.writeInt(httpRequest, ClientHTTPRequestSchema.MSG_HTTPGET_100_FIELD_PORT_1, port);
    		PipeWriter.writeUTF8(httpRequest, ClientHTTPRequestSchema.MSG_HTTPGET_100_FIELD_HOST_2, host);
    		PipeWriter.writeUTF8(httpRequest, ClientHTTPRequestSchema.MSG_HTTPGET_100_FIELD_PATH_3, route);
    		PipeWriter.writeInt(httpRequest, ClientHTTPRequestSchema.MSG_HTTPGET_100_FIELD_LISTENER_10, System.identityHashCode(listener));
    		PipeWriter.writeUTF8(httpRequest, ClientHTTPRequestSchema.MSG_HTTPGET_100_FIELD_HEADERS_7, "");
    		    		
    		PipeWriter.publishWrites(httpRequest);
            
    		builder.releasePubSubTraffic(1, this);
    	            
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
    	if (PipeWriter.hasRoomForWrite(goPipe) && PipeWriter.tryWriteFragment(httpRequest, ClientHTTPRequestSchema.MSG_HTTPPOST_101)) {
                	    
    		PipeWriter.writeInt(httpRequest, ClientHTTPRequestSchema.MSG_HTTPPOST_101_FIELD_PORT_1, port);
    		PipeWriter.writeUTF8(httpRequest, ClientHTTPRequestSchema.MSG_HTTPPOST_101_FIELD_HOST_2, host);
    		PipeWriter.writeUTF8(httpRequest, ClientHTTPRequestSchema.MSG_HTTPPOST_101_FIELD_PATH_3, route);
    		PipeWriter.writeInt(httpRequest, ClientHTTPRequestSchema.MSG_HTTPPOST_101_FIELD_LISTENER_10, System.identityHashCode(listener));

    		builder.releasePubSubTraffic(1, this);
            
            PayloadWriter<ClientHTTPRequestSchema> pw = (PayloadWriter<ClientHTTPRequestSchema>) Pipe.outputStream(httpRequest);
           
            pw.openField(ClientHTTPRequestSchema.MSG_HTTPPOST_101_FIELD_PAYLOAD_5, this, builder);  
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
    	
    	if (publishTopic(topic, writable)) {
			return;
		} else { 
			logger.warn("unable to publish on topic {} must wait.",topic);
			while (!publishTopic(topic, writable)) {
				Thread.yield();
			}
		}
    }
    
    /**
     * Opens a topic on this channel for writing.
     *
     * @param topic Topic to open.
     *
     * @return {@link PayloadWriter} attached to the given topic.
     */
    public boolean publishTopic(CharSequence topic, PubSubWritable writable) {
		assert((0 != (initFeatures & DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";

        assert(writable != null);
        if (PipeWriter.hasRoomForWrite(goPipe) && 
        	PipeWriter.tryWriteFragment(messagePubSub, MessagePubSub.MSG_PUBLISH_103)) {
            
        	PipeWriter.writeUTF8(messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1, topic);         
        	
            PubSubWriter pw = (PubSubWriter) Pipe.outputStream(messagePubSub);
            
            pw.openField(MessagePubSub.MSG_PUBLISH_103_FIELD_PAYLOAD_3,this,builder);
            writable.write(pw);//TODO: cool feature, writable to return false to abandon write..
            
            pw.publish();
            
            return true;
            
        } else {
            return false;
        }
    }

    public void presumePublishStructuredTopic(CharSequence topic, PubSubStructuredWritable writable) {
    	
    	if (publishStructuredTopic(topic, writable)) {
			return;
		} else { 
			logger.warn("unable to publish on topic {} must wait.",topic);
			while (!publishStructuredTopic(topic, writable)) {
				Thread.yield();
			}
		}
    }
        
    public boolean publishStructuredTopic(CharSequence topic, PubSubStructuredWritable writable) {
 	    assert((0 != (initFeatures & DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";

    	assert(writable != null);
        assert(null != goPipe);
        assert(null != messagePubSub);
        if (PipeWriter.hasRoomForWrite(goPipe) 
        	&& PipeWriter.tryWriteFragment(messagePubSub, MessagePubSub.MSG_PUBLISH_103)) {

        	PipeWriter.writeUTF8(messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1, topic);            
                    	
            PubSubWriter pw = (PubSubWriter) Pipe.outputStream(messagePubSub);
            pw.openField(MessagePubSub.MSG_PUBLISH_103_FIELD_PAYLOAD_3,this,builder);            
            writable.write(pw); //TODO: cool feature, writable to return false to abandon write.. 
            pw.publish();
    
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
				NetResponseWriter::close); //no type and no body so use null
	}

	public boolean publishHTTPResponse(HTTPFieldReader w, 
										            int statusCode, final int context, 
										            HTTPContentTypeDefaults contentType,
										            NetWritable writable) {
		
		 assert((0 != (initFeatures & NET_RESPONDER))) : "CommandChannel must be created with NET_RESPONDER flag";

		return publishHTTPResponse(w.getConnectionId(), w.getSequenceCode(),
				                statusCode, context, contentType, writable);
	}	

	public boolean publishHTTPResponse(long connectionId, long sequenceCode, 
			                                            int statusCode, final int context, 
			                                            HTTPContentTypeDefaults contentType,
			                                            NetWritable writable) {
		
		assert((0 != (initFeatures & NET_RESPONDER))) : "CommandChannel must be created with NET_RESPONDER flag";

		final int sequenceNo = 0xFFFFFFFF & (int)sequenceCode;
		final int parallelIndex = 0xFFFFFFFF & (int)(sequenceCode>>32);
	
		assert(1==lastResponseWriterFinished) : "Previous write was not ended can not start another.";
			
		Pipe<ServerResponseSchema> pipe = netResponse.length>1 ? netResponse[parallelIndex] : netResponse[0];
		
		if (!Pipe.hasRoomForWrite(pipe)) {
			return false;
		}		

		///////////////////////////////////////
		//message 1 which contains the headers
		//////////////////////////////////////
				
		Pipe.addMsgIdx(pipe, ServerResponseSchema.MSG_TOCHANNEL_100);
		Pipe.addLongValue(connectionId, pipe);
		Pipe.addIntValue(sequenceNo, pipe);	
		
		NetResponseWriter outputStream = (NetResponseWriter)Pipe.outputStream(pipe);	
		final int headerBlobPosition = Pipe.getWorkingBlobHeadPosition(pipe);
		//outputStream.length = 0; //TODO: for lenghth check...
		DataOutputBlobWriter.openFieldAtPosition(outputStream, headerBlobPosition); 	//no context, that will come in the second message 
        
		//for the var field we store this as meta then length
		final long positionOfLen = 1+Pipe.workingHeadPosition(pipe);	
		DataOutputBlobWriter.closeLowLevelMaxVarLenField(outputStream);
		assert(pipe.maxVarLen == Pipe.slab(pipe)[((int)positionOfLen) & Pipe.slabMask(pipe)]) : "expected max var field length";
		
		Pipe.addIntValue(0, pipe); 	//no context, that will come in the second message		
		//the full blob size of this message is very large to ensure we have room later...
		//this call allows for the following message to be written after this messages blob data
		int consumed = Pipe.writeTrailingCountOfBytesConsumed(outputStream.getPipe()); 
		assert(pipe.maxVarLen == consumed);
		Pipe.confirmLowLevelWrite(pipe); 
		//Stores this publish until the next message is complete and published
		Pipe.storeUnpublishedWrites(outputStream.getPipe());
	
		
		//////////////////////////////////////////
		//begin message 2 which contains the body
		//////////////////////////////////////////
		Pipe.addMsgIdx(pipe, ServerResponseSchema.MSG_TOCHANNEL_100);
		Pipe.addLongValue(connectionId, pipe);
		Pipe.addIntValue(sequenceNo, pipe);	
		
		outputStream = (NetResponseWriter)Pipe.outputStream(pipe);
		outputStream.openField(headerBlobPosition, positionOfLen, statusCode, context, contentType);
				
		//TODO: keep track of was this the end and flag error??
		lastResponseWriterFinished = 1&(context>>ServerCoordinator.END_RESPONSE_SHIFT);

		writable.write(outputStream);
		return true;

	}


	public boolean publishHTTPResponseContinuation(long connectionId, long sequenceCode, 
			                                    int context, NetWritable writable) {
		
    	assert((0 != (initFeatures & NET_RESPONDER))) : "CommandChannel must be created with NET_RESPONDER flag";

		final int sequenceNo = 0xFFFFFFFF & (int)sequenceCode;
		final int parallelIndex = 0xFFFFFFFF & (int)(sequenceCode>>32);		
		
		assert(0==lastResponseWriterFinished) : "Unable to find write in progress, nothing to continue with";
		
		Pipe<ServerResponseSchema> pipe = netResponse.length>1 ? netResponse[parallelIndex] : netResponse[0];

		if (!Pipe.hasRoomForWrite(pipe)) {
			return false;
		}
				
		Pipe.addMsgIdx(pipe, ServerResponseSchema.MSG_TOCHANNEL_100);
		Pipe.addLongValue(connectionId, pipe);
		Pipe.addIntValue(sequenceNo, pipe);	
		NetResponseWriter outputStream = (NetResponseWriter)Pipe.outputStream(pipe);
	
		outputStream.openField(context);
				
		lastResponseWriterFinished = 1&(context>>ServerCoordinator.END_RESPONSE_SHIFT);
		
		writable.write(outputStream);		
		return true;
	}

	public static void publishGo(int count, int pipeIdx, GreenCommandChannel<?> gcc) {
		
		if(PipeWriter.tryWriteFragment(gcc.goPipe, TrafficOrderSchema.MSG_GO_10)) {                 
            PipeWriter.writeInt(gcc.goPipe, TrafficOrderSchema.MSG_GO_10_FIELD_PIPEIDX_11, pipeIdx);
            PipeWriter.writeInt(gcc.goPipe, TrafficOrderSchema.MSG_GO_10_FIELD_COUNT_12, count);
            PipeWriter.publishWrites(gcc.goPipe);
        } else {
            throw new UnsupportedOperationException("Was already check and should not have run out of space.");
        }
		
	}
	
}