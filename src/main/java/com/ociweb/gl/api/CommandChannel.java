package com.ociweb.gl.api;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import com.ociweb.gl.impl.BuilderImpl;
import com.ociweb.gl.impl.schema.MessagePubSub;
import com.ociweb.gl.impl.schema.MessageSubscription;
import com.ociweb.gl.impl.schema.TrafficOrderSchema;
import com.ociweb.pronghorn.network.AbstractRestStage;
import com.ociweb.pronghorn.network.ServerCoordinator;
import com.ociweb.pronghorn.network.config.HTTPContentType;
import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;
import com.ociweb.pronghorn.network.config.HTTPRevisionDefaults;
import com.ociweb.pronghorn.network.schema.ClientHTTPRequestSchema;
import com.ociweb.pronghorn.network.schema.HTTPRequestSchema;
import com.ociweb.pronghorn.network.schema.ServerResponseSchema;
import com.ociweb.pronghorn.pipe.DataInputBlobReader;
import com.ociweb.pronghorn.pipe.DataOutputBlobWriter;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeConfig;
import com.ociweb.pronghorn.pipe.PipeWriter;
import com.ociweb.pronghorn.pipe.RawDataSchema;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;

/**
 * Represents a dedicated channel for communicating with a single device
 * or resource on an IoT system.
 */
public class CommandChannel {

    private final Pipe<TrafficOrderSchema> goPipe;
    private final Pipe<MessagePubSub> messagePubSub;
    private final Pipe<ClientHTTPRequestSchema> httpRequest;
    
    private final Pipe<ServerResponseSchema>[] netResponse;
    private Optional<NetResponseWriter>[] optionalResponseWriter;
    
    
    private Optional<PayloadWriter> optionalPipSubWriter;
    
    private int lastResponseWriterFinished = 1;//starting in the "end" state
    
    private final Pipe<MessagePubSub>[] exclusivePubSub;
    private String[] exclusiveTopics =  new String[0];//TODO: make empty
        
    
    protected AtomicBoolean aBool = new AtomicBoolean(false);   

    protected static final long MS_TO_NS = 1_000_000;
         
    private Object listener;

    protected final int subPipeIdx         = 0;
    protected final int netPipeIdx         = 1;
    protected final int netResponsePipeIdx = 2;
    protected final int goPipeIdx          = 3;
    
    
    public static final int DYNAMIC_MESSAGING = 1<<0;
    public static final int STATE_MACHINE = DYNAMIC_MESSAGING;//state machine is based on DYNAMIC_MESSAGING;    
    
    public static final int NET_REQUESTER     = 1<<1;
    public static final int NET_RESPONDER     = 1<<2;
    public static final int ALL = DYNAMIC_MESSAGING | NET_REQUESTER | NET_RESPONDER;
    
    private final int parallelInstanceId;    
    private BuilderImpl builder;

    private final static int[] EMPTY = new int[0];
    private final static Pipe[] EMPTY_PIPES = new Pipe[0];
    
    private Pipe<HTTPRequestSchema>[] restRequests = EMPTY_PIPES;
	
    
    private final int MAX_TEXT_LENGTH = 64;
    private final Pipe<RawDataSchema> digitBuffer = new Pipe<RawDataSchema>(new PipeConfig<RawDataSchema>(RawDataSchema.instance,3,MAX_TEXT_LENGTH));
    
	public final int maxHTTPContentLength;
	
    
    public CommandChannel(GraphManager gm, BuilderImpl hardware,
			      		    int parallelInstanceId,
				            PipeConfig<MessagePubSub> pubSubConfig,
				            PipeConfig<ClientHTTPRequestSchema> netRequestConfig,
				            PipeConfig<TrafficOrderSchema> goPipeConfig,
				            PipeConfig<ServerResponseSchema> netResponseconfig
				           ) {
    	this(gm,hardware,ALL, parallelInstanceId, pubSubConfig, netRequestConfig, goPipeConfig, netResponseconfig);
    }
    
    public CommandChannel(GraphManager gm, BuilderImpl hardware,
    					  int features,
    					  int parallelInstanceId,
                          PipeConfig<MessagePubSub> pubSubConfig,
                          PipeConfig<ClientHTTPRequestSchema> netRequestConfig,
                          PipeConfig<TrafficOrderSchema> goPipeConfig,
                          PipeConfig<ServerResponseSchema> netResponseconfig
    		             ) {
    	
       this.parallelInstanceId = parallelInstanceId;
                             
       this.messagePubSub = ((features & DYNAMIC_MESSAGING) == 0) ? null : newPubSubPipe(pubSubConfig);
       this.httpRequest   = ((features & NET_REQUESTER) == 0)     ? null : newNetRequestPipe(netRequestConfig);

       this.digitBuffer.initBuffers();
       
       /////////////////////////
       //build pipes for sending out the REST server responses
       ////////////////////////       
       Pipe<ServerResponseSchema>[] netResponse = null;
       if ((features & NET_RESPONDER) != 0) {
    	   
    	   if (-1 == parallelInstanceId) {
    		   //we have only a single instance of this object so we must have 1 pipe for each parallel track
    		   int p = hardware.parallelism();
    		   netResponse = ( Pipe<ServerResponseSchema>[])new Pipe[p];
    		   while (--p>=0) {
    			   netResponse[p] = hardware.newNetResposnePipe(netResponseconfig, p);
    		   }
    	   } else {
    		   //we have multiple instances of this object so each only has 1 pipe
    		   netResponse = ( Pipe<ServerResponseSchema>[])new Pipe[1];
    		   netResponse[0] = hardware.newNetResposnePipe(netResponseconfig, parallelInstanceId);
    	   }
       }
       this.netResponse = netResponse;
       ///////////////////////////
       
       
       if (null != this.netResponse) {
    	   
    	   int x = this.netResponse.length;
    	   optionalResponseWriter = (Optional<NetResponseWriter>[])new Optional[x];
    	   
    	   while(--x>=0) {
    	   
	    	   if (!Pipe.isInit(netResponse[x])) {
	    		   //hack for now.
	    		   netResponse[x].initBuffers();
	    	   }
	    	   //prebuild optionals so they need not be created at runtime and can be re-used
	    	   optionalResponseWriter[x] = Optional.of((NetResponseWriter)Pipe.outputStream(netResponse[x]));
	    	   
    	   }
       }
       
       if (null != this.messagePubSub) {
    	   if (!Pipe.isInit(messagePubSub)) {
    		   messagePubSub.initBuffers();
    	   }
    	   //prebuild optionals so they need not be created at runtime and can be re-used
    	   optionalPipSubWriter = Optional.of((PayloadWriter)Pipe.outputStream(messagePubSub));
       }
       
       
       //we only need go pipe for some features and when they are used we create the go pipe
       this.goPipe        = (this.messagePubSub == null)          ? null : newGoPipe(goPipeConfig);

       ///////////////////

       int e = this.exclusiveTopics.length;
       this.exclusivePubSub = (Pipe<MessagePubSub>[])new Pipe[e];
       while (--e>=0) {
    	   exclusivePubSub[e] = newPubSubPipe(pubSubConfig);
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
	   
       this.builder = hardware;
       
    }

	
    
    Pipe<?>[] getOutputPipes() {
    	
    	int length = 0;
    	
    	if (null != messagePubSub) {
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
				return new PayloadWriter(this);
			}    		
    	};
    }
    
    private static Pipe<ClientHTTPRequestSchema> newNetRequestPipe(PipeConfig<ClientHTTPRequestSchema> config) {
    	
    	new Exception("created client pipe ").printStackTrace();
    	
    	return new Pipe<ClientHTTPRequestSchema>(config) {
			@SuppressWarnings("unchecked")
			@Override
			protected DataOutputBlobWriter<ClientHTTPRequestSchema> createNewBlobWriter() {
				return new PayloadWriter(this);
			}    		
    	};
    }


	private Pipe<TrafficOrderSchema> newGoPipe(PipeConfig<TrafficOrderSchema> goPipeConfig) {
		return new Pipe<TrafficOrderSchema>(goPipeConfig);
	}
        
    
    
    void setListener(Object listener) {
        if (null != this.listener) {
            throw new UnsupportedOperationException("Bad Configuration, A CommandChannel can only be held and used by a single listener lambda/class");
        }
        this.listener = listener;
        
        
        
        
        
    }

    
    protected void publishGo(int count, int pipeIdx) {
        if(PipeWriter.tryWriteFragment(goPipe, TrafficOrderSchema.MSG_GO_10)) {                 
            PipeWriter.writeInt(goPipe, TrafficOrderSchema.MSG_GO_10_FIELD_PIPEIDX_11, pipeIdx);
            PipeWriter.writeInt(goPipe, TrafficOrderSchema.MSG_GO_10_FIELD_COUNT_12, count);
            PipeWriter.publishWrites(goPipe);
        } else {
            throw new UnsupportedOperationException("Was already check and should not have run out of space.");
        }
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
    		PipeWriter.publishWrites(httpRequest);
            
    		publishGo(1,subPipeIdx);
            
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
    public PayloadWriter httpPost(CharSequence host, int port, CharSequence route, HTTPResponseListener listener) {
    	if (PipeWriter.hasRoomForWrite(goPipe) && PipeWriter.tryWriteFragment(httpRequest, ClientHTTPRequestSchema.MSG_HTTPPOST_101)) {
                	    
    		PipeWriter.writeInt(httpRequest, ClientHTTPRequestSchema.MSG_HTTPPOST_101_FIELD_PORT_1, port);
    		PipeWriter.writeUTF8(httpRequest, ClientHTTPRequestSchema.MSG_HTTPPOST_101_FIELD_HOST_2, host);
    		PipeWriter.writeUTF8(httpRequest, ClientHTTPRequestSchema.MSG_HTTPPOST_101_FIELD_PATH_3, route);
    		PipeWriter.writeInt(httpRequest, ClientHTTPRequestSchema.MSG_HTTPPOST_101_FIELD_LISTENER_10, System.identityHashCode(listener));

            publishGo(1,subPipeIdx);
            
            PayloadWriter pw = (PayloadWriter) Pipe.outputStream(messagePubSub);    
            pw.openField(ClientHTTPRequestSchema.MSG_HTTPPOST_101_FIELD_PAYLOAD_5,this);  
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
        if (PipeWriter.hasRoomForWrite(goPipe) && PipeWriter.tryWriteFragment(messagePubSub, MessagePubSub.MSG_SUBSCRIBE_100)) {
            
            PipeWriter.writeInt(messagePubSub, MessagePubSub.MSG_SUBSCRIBE_100_FIELD_SUBSCRIBERIDENTITYHASH_4, System.identityHashCode(listener));
            PipeWriter.writeUTF8(messagePubSub, MessagePubSub.MSG_SUBSCRIBE_100_FIELD_TOPIC_1, topic);
            
            PipeWriter.publishWrites(messagePubSub);
            
            publishGo(1,subPipeIdx);
            
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
        if (PipeWriter.hasRoomForWrite(goPipe) && PipeWriter.tryWriteFragment(messagePubSub, MessagePubSub.MSG_UNSUBSCRIBE_101)) {
            
            PipeWriter.writeInt(messagePubSub, MessagePubSub.MSG_SUBSCRIBE_100_FIELD_SUBSCRIBERIDENTITYHASH_4, System.identityHashCode(listener));
            PipeWriter.writeUTF8(messagePubSub, MessagePubSub.MSG_UNSUBSCRIBE_101_FIELD_TOPIC_1, topic);
            
            PipeWriter.publishWrites(messagePubSub);
            
            publishGo(1,subPipeIdx);
            
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
    	 assert(builder.isValidState(state));
    	 if (!builder.isValidState(state)) {
    		 throw new UnsupportedOperationException("no match "+state.getClass());
    	 }
    	
    	 if (PipeWriter.hasRoomForWrite(goPipe) && PipeWriter.tryWriteFragment(messagePubSub, MessagePubSub.MSG_CHANGESTATE_70)) {

    		 PipeWriter.writeInt(messagePubSub, MessagePubSub.MSG_CHANGESTATE_70_FIELD_ORDINAL_7,  state.ordinal());
             PipeWriter.publishWrites(messagePubSub);
             
             publishGo(1,subPipeIdx);
    		 return true;
    	 }

    	return false;
    	
    }

    /**
     * Opens a topic on this channel for writing.
     *
     * @param topic Topic to open.
     *
     * @return {@link PayloadWriter} attached to the given topic.
     */
    public  Optional<PayloadWriter> openTopic(CharSequence topic) { //TODO: urgent, must add support for MQTT style routing and wild cards.
        
        if (PipeWriter.hasRoomForWrite(goPipe) && PipeWriter.tryWriteFragment(messagePubSub, MessagePubSub.MSG_PUBLISH_103)) {
            
            PipeWriter.writeUTF8(messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1, topic);            
            PayloadWriter pw = (PayloadWriter) Pipe.outputStream(messagePubSub);
            pw.openField(MessagePubSub.MSG_PUBLISH_103_FIELD_PAYLOAD_3,this);            
                        
            return optionalPipSubWriter;
            
        } else {
            //breaks fluent api use because there is no place to write the data.
            //makers/students will have a lesson where we do new Optional(openTopic("topic"))  then  ifPresent() to only send when we can
            return Optional.empty();
        }
    }

	public Optional<NetResponseWriter> openHTTPResponse(long connectionId, long sequenceCode, int statusCode, int context, HTTPContentTypeDefaults contentType, int length) {

		final int sequenceNo = 0xFFFFFFFF & (int)sequenceCode;
		final int parallelIndex = 0xFFFFFFFF & (int)(sequenceCode>>32);
	
		assert(1==lastResponseWriterFinished) : "Previous write was not ended can not start another.";
			
		Pipe<ServerResponseSchema> pipe = netResponse.length>1 ? netResponse[parallelIndex] : netResponse[0];
		
		if (!Pipe.hasRoomForWrite(pipe)) {
			return Optional.empty();
		}
		
		
		Pipe.addMsgIdx(pipe, ServerResponseSchema.MSG_TOCHANNEL_100);
		Pipe.addLongValue(connectionId, pipe);
		Pipe.addIntValue(sequenceNo, pipe);	
		NetResponseWriter outputStream = (NetResponseWriter)Pipe.outputStream(pipe);

		outputStream.openField(context);
				
		int connectionIsClosed = 1&(context>>ServerCoordinator.CLOSE_CONNECTION_SHIFT);
		byte[] revisionBytes = HTTPRevisionDefaults.HTTP_1_1.getBytes();
		
		byte[] etagBytes = null;//TODO: nice feature to add later
		
		writeHeaderImpl(outputStream, statusCode, contentType, length, connectionIsClosed, revisionBytes, etagBytes);

		lastResponseWriterFinished = 1&(context>>ServerCoordinator.END_RESPONSE_SHIFT);
		//TODO: keep track of was this the end and flag error??
		
		return optionalResponseWriter.length>1 ? optionalResponseWriter[parallelIndex] : optionalResponseWriter[0]; 
	}

	public Optional<NetResponseWriter> openHTTPResponseContinuation(long connectionId, long sequenceCode, int context) {
		
		final int sequenceNo = 0xFFFFFFFF & (int)sequenceCode;
		final int parallelIndex = 0xFFFFFFFF & (int)(sequenceCode>>32);		
		
		assert(0==lastResponseWriterFinished) : "Unable to find write in progress, nothing to continue with";
		
		Pipe<ServerResponseSchema> pipe = netResponse.length>1 ? netResponse[parallelIndex] : netResponse[0];

		if (!Pipe.hasRoomForWrite(pipe)) {
			return Optional.empty();
		}
		
		
		Pipe.addMsgIdx(pipe, ServerResponseSchema.MSG_TOCHANNEL_100);
		Pipe.addLongValue(connectionId, pipe);
		Pipe.addIntValue(sequenceNo, pipe);	
		NetResponseWriter outputStream = (NetResponseWriter)Pipe.outputStream(pipe);
	
		outputStream.openField(context);
				
		lastResponseWriterFinished = 1&(context>>ServerCoordinator.END_RESPONSE_SHIFT);
		
		return optionalResponseWriter[parallelIndex];
	}


	private <T extends HTTPContentType>void writeHeaderImpl(final DataOutputBlobWriter<ServerResponseSchema> outputStream,	int status, T contentType, int length, final int conStateIdx, byte[] revisionBytes,
			byte[] etagBytes) {
		
				byte[] lenAsBytes = null;//TODO: nice feature to add of knowing length up front.
				int lenAsBytesPos = 0;
			    int lenAsBytesLen = 0;
			    int lenAsBytesMask = 0;
			
				if (length>=0) {
									
					  int addSize = Pipe.addMsgIdx(digitBuffer, RawDataSchema.MSG_CHUNKEDSTREAM_1);
					  int digitsLen = Pipe.addLongAsUTF8(digitBuffer, length);
				      Pipe.publishWrites(digitBuffer);
				      Pipe.confirmLowLevelWrite(digitBuffer, addSize);
				      
				      ////////////
										          
			          int msgIdx = Pipe.takeMsgIdx(digitBuffer); 
			          int meta = Pipe.takeRingByteMetaData(digitBuffer);
			          lenAsBytesLen = Pipe.takeRingByteLen(digitBuffer);
			          lenAsBytesPos = Pipe.bytePosition(meta, digitBuffer, lenAsBytesLen);
			          lenAsBytes = Pipe.byteBackingArray(meta, digitBuffer);
			          lenAsBytesMask = Pipe.blobMask(digitBuffer);
			          
			          assert(digitsLen == lenAsBytesLen) : "byte written should be the same as bytes consumed";
			          
			          Pipe.confirmLowLevelRead(digitBuffer, Pipe.sizeOf(RawDataSchema.instance,RawDataSchema.MSG_CHUNKEDSTREAM_1));
			          Pipe.releaseReadLock(digitBuffer);
												
				}				

				
				AbstractRestStage.writeHeader(revisionBytes, status, 0, etagBytes, contentType.getBytes(), 
							    lenAsBytes, lenAsBytesPos, lenAsBytesLen, lenAsBytesMask, 
							    false, null, 0,0,0, outputStream, conStateIdx);
	}

	
	//package protected
	static void setRestRoutes(CommandChannel cc, int[] routes, final int parallelInstance) {
		assert(routes.length>0) : "API requires 1 or more routes";
		
		int r = routes.length;
				
		int p;
		if (-1 == parallelInstance) {
			p = cc.builder.parallelism();
		} else {
			p = 1;
		}
		int count = r*p;
		
		cc.restRequests = new Pipe[count];
		int idx = count;
		
		while (--r >= 0) {	
			int routeIndex = routes[r];
			//if parallel is -1 we need to do one for each
						
			int x = p;
			while (--x>=0) {
							
				Pipe<HTTPRequestSchema> pipe = cc.builder.createHTTPRequestPipe(cc.builder.restPipeConfig.grow2x(), routeIndex, -1 == parallelInstance ? x : parallelInstance);				
				
				cc.restRequests[--idx] = pipe;
	            
			}            
		}
		
		
	}


    Pipe<HTTPRequestSchema>[] getHTTPRequestPipes() {
    	return restRequests;
    }


}