package com.ociweb.gl.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.gl.api.MQTTBridge;
import com.ociweb.gl.api.MQTTWritable;
import com.ociweb.gl.api.MQTTWriter;
import com.ociweb.gl.api.MsgRuntime;
import com.ociweb.gl.impl.schema.IngressMessages;
import com.ociweb.gl.impl.stage.EgressConverter;
import com.ociweb.gl.impl.stage.EgressMQTTStage;
import com.ociweb.gl.impl.stage.IngressConverter;
import com.ociweb.gl.impl.stage.IngressMQTTStage;
import com.ociweb.pronghorn.network.mqtt.MQTTClientGraphBuilder;
import com.ociweb.pronghorn.network.mqtt.MQTTEncoder;
import com.ociweb.pronghorn.network.schema.MQTTClientRequestSchema;
import com.ociweb.pronghorn.network.schema.MQTTClientResponseSchema;
import com.ociweb.pronghorn.pipe.DataOutputBlobWriter;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeConfig;
import com.ociweb.pronghorn.pipe.PipeWriter;
import com.ociweb.pronghorn.stage.test.PipeCleanerStage;
import com.ociweb.pronghorn.stage.test.PipeNoOp;

public class MQTTConfigImpl extends BridgeConfigImpl<MQTTConfigTransmission,MQTTConfigSubscription> implements MQTTBridge {

	private final static Logger logger = LoggerFactory.getLogger(MQTTConfigImpl.class);
	
	private final CharSequence host;
	private final int port;
	private final CharSequence clientId;
	private int keepAliveSeconds = 10; //default
	//
	private CharSequence user = null;
	private CharSequence pass = null;
	private CharSequence willTopic = null;
	private MQTTWritable willPayload = null;
	//
	private int flags;
	private boolean isTLS; //derive from host/port args
	private final short maxInFlight;
	private final int maximumLenghOfVariableLengthFields;
	//
	private final BuilderImpl builder;
	//
	private boolean isImmutable;
	
	private Pipe<MQTTClientRequestSchema> clientRequest;
	private Pipe<MQTTClientResponseSchema> clientResponse;
	private final long rate;
	
	private int subscriptionQoS = 0;

	private int transmissionFieldQOS = 0; 
	private int transmissionFieldRetain = 0;
	
	MQTTConfigImpl(CharSequence host, int port, CharSequence clientId,
			       BuilderImpl builder, long rate, 
			       short maxInFlight, int maxMessageLength) {
		
		this.host = host;
		this.port = port;
		this.clientId = clientId;
		this.builder = builder;
		this.rate = rate;
		this.maxInFlight = maxInFlight;
		this.maximumLenghOfVariableLengthFields = maxMessageLength;
		
	}
	
	
    private static Pipe<MQTTClientRequestSchema> newClientRequestPipe(PipeConfig<MQTTClientRequestSchema> config) {
    	return new Pipe<MQTTClientRequestSchema>(config) {
			@SuppressWarnings("unchecked")
			@Override
			protected DataOutputBlobWriter<MQTTClientRequestSchema> createNewBlobWriter() {
				return new MQTTWriter(this);
			}    		
    	};
    }
	
	//send on construction, do not save		
	private void publishBrokerConfig(Pipe<MQTTClientRequestSchema> output) {
		PipeWriter.presumeWriteFragment(output, MQTTClientRequestSchema.MSG_BROKERCONFIG_100);
			
	    PipeWriter.writeUTF8(output,MQTTClientRequestSchema.MSG_BROKERCONFIG_100_FIELD_HOST_26, (CharSequence) host);
	    PipeWriter.writeInt(output,MQTTClientRequestSchema.MSG_BROKERCONFIG_100_FIELD_PORT_27, port);
	    PipeWriter.publishWrites(output);
				
	}
	
	//send upon complete construction
	private void publishConnect(Pipe<MQTTClientRequestSchema> output) {

		PipeWriter.presumeWriteFragment(output, MQTTClientRequestSchema.MSG_CONNECT_1);
			
	    PipeWriter.writeInt(output,MQTTClientRequestSchema.MSG_CONNECT_1_FIELD_KEEPALIVESEC_28, keepAliveSeconds);
	    PipeWriter.writeInt(output,MQTTClientRequestSchema.MSG_CONNECT_1_FIELD_FLAGS_29, flags);
	    PipeWriter.writeUTF8(output,MQTTClientRequestSchema.MSG_CONNECT_1_FIELD_CLIENTID_30, (CharSequence) clientId);
	    PipeWriter.writeUTF8(output,MQTTClientRequestSchema.MSG_CONNECT_1_FIELD_WILLTOPIC_31, (CharSequence) willTopic);
	    
	    DataOutputBlobWriter<MQTTClientRequestSchema> writer = PipeWriter.outputStream(output);
	    DataOutputBlobWriter.openField(writer);	
	    if(null!=willPayload) {
	    	willPayload.write((MQTTWriter)writer);
	    }
	    DataOutputBlobWriter.closeHighLevelField(writer, MQTTClientRequestSchema.MSG_CONNECT_1_FIELD_WILLPAYLOAD_32);
	    
	    PipeWriter.writeUTF8(output,MQTTClientRequestSchema.MSG_CONNECT_1_FIELD_USER_33, (CharSequence) user);
	    PipeWriter.writeUTF8(output,MQTTClientRequestSchema.MSG_CONNECT_1_FIELD_PASS_34, (CharSequence) pass);
	    PipeWriter.publishWrites(output);
			
	}
	
	public MQTTBridge keepAliveSeconds(int seconds) {
		if (isImmutable) {
			throw new UnsupportedOperationException("Mutations must happen earlier.");
		}
		keepAliveSeconds = seconds;
		return this;
	}
	
	/**
	 * Clean session ensures the server will not remember state from
	 * previous connections from this client.  In order to ensure QOS 
	 * across restarts of the client this should be set to true.
	 * 
	 * By default this is false.
	 * 
	 * @param clean
	 */
	public MQTTBridge cleanSession(boolean clean) {
		if (isImmutable) {
			throw new UnsupportedOperationException("Mutations must happen earlier.");
		}
		flags = setBitByBoolean(flags, clean, MQTTEncoder.CONNECT_FLAG_CLEAN_SESSION_1);
		return this;
	}

	private int setBitByBoolean(int target, boolean clean, int bit) {
		if (clean) {
			target = target|bit;
		} else {
			target = (~target)&bit;
		}
		return target;
	}
	
	public MQTTBridge authentication(CharSequence user, CharSequence pass) {
		if (isImmutable) {
			throw new UnsupportedOperationException("Mutations must happen earlier.");
		}
		flags |= MQTTEncoder.CONNECT_FLAG_USERNAME_7;
		flags |= MQTTEncoder.CONNECT_FLAG_PASSWORD_6;		
				
		this.user = user;
		this.pass = pass;
		
		assert(null != user);
		assert(null != pass);
		
		return this;
	}
	
	@Override
	public MQTTBridge subscriptionQoS(int value) {
		subscriptionQoS = value;
		return this;
	}

	@Override
	public MQTTBridge transmissionOoS(int value) {
		transmissionFieldQOS = value;
		return this;
	}

	@Override
	public MQTTBridge transmissionRetain(boolean value) {		
		transmissionFieldRetain = setBitByBoolean(transmissionFieldRetain, value, MQTTEncoder.CONNECT_FLAG_WILL_RETAIN_5 );
		return this;
	}

	public MQTTBridge will(boolean retain, int qos, CharSequence topic, MQTTWritable write ) {
		if (isImmutable) {
			throw new UnsupportedOperationException("Mutations must happen earlier.");
		}
		assert(null!=topic);
		assert(null!=write);
		
		flags = setBitByBoolean(flags, retain, MQTTEncoder.CONNECT_FLAG_WILL_RETAIN_5);
		
		flags = setBitByBoolean(flags, (2&qos)!=0, MQTTEncoder.CONNECT_FLAG_WILL_QOS_3);
		flags = setBitByBoolean(flags, (1&qos)!=0, MQTTEncoder.CONNECT_FLAG_WILL_QOS_4);
		
		flags |= MQTTEncoder.CONNECT_FLAG_WILL_FLAG_2;
		
		this.willTopic = topic;
		this.willPayload = write;
		
		return this;
	}

	private void ensureConnected() {
		if (isImmutable) {
			return;
		} else {
			//No need for this pipe to be large since we can only get one at a time from the MessagePubSub feeding EngressMQTTStage
			int egressPipeLength = 32;
			
			PipeConfig<MQTTClientRequestSchema> newPipeConfig = MQTTClientRequestSchema.instance.newPipeConfig(egressPipeLength, maximumLenghOfVariableLengthFields);
			
			clientRequest = newClientRequestPipe(newPipeConfig);
			clientRequest.initBuffers();
			
			PipeConfig<MQTTClientResponseSchema> newPipeConfig2 = MQTTClientResponseSchema.instance.newPipeConfig((int) maxInFlight, maximumLenghOfVariableLengthFields);
			
			clientResponse = new Pipe<MQTTClientResponseSchema>(newPipeConfig2);

			byte totalConnectionsInBits = 2; //only 4 brokers
			short maxPartialResponses = 2;
			
			MQTTClientGraphBuilder.buildMQTTClientGraph(builder.gm, isTLS, 
					                              maxInFlight, maximumLenghOfVariableLengthFields, 
					                              clientRequest, clientResponse, rate, 
					                              totalConnectionsInBits,
					                              maxPartialResponses);
			
			//send the broker details
			publishBrokerConfig(clientRequest);
			//send the connect msg
			publishConnect(clientRequest);			
			isImmutable = true;
		}
	}
	

	private final int code =  System.identityHashCode(this);
	private CharSequence[] internalTopicsXmit = new CharSequence[0];
	private CharSequence[] externalTopicsXmit = new CharSequence[0];
	private EgressConverter[] convertersXmit = new EgressConverter[0];
	private int[] qosXmit = new int[0];
	
	private CharSequence[] internalTopicsSub = new CharSequence[0];
	private CharSequence[] externalTopicsSub = new CharSequence[0];
	private IngressConverter[] convertersSub = new IngressConverter[0];
    private int[] qosSub = new int[0];		
	
	@Override
	public long addSubscription(CharSequence internalTopic, CharSequence externalTopic) {
		ensureConnected();
		
		internalTopicsSub = grow(internalTopicsSub, internalTopic);
		externalTopicsSub = grow(externalTopicsSub, externalTopic);
		convertersSub = grow(convertersSub,IngressMQTTStage.copyConverter);
		qosSub = grow(qosSub, subscriptionQoS);
		
		assert(internalTopicsSub.length == externalTopicsSub.length);
		assert(internalTopicsSub.length == convertersSub.length);
		assert(internalTopicsSub.length == qosSub.length);
		
		return internalTopicsSub.length-1;
	}
	
	@Override
	public long addSubscription(CharSequence internalTopic, CharSequence externalTopic, IngressConverter converter) {
		ensureConnected();
		
		internalTopicsSub = grow(internalTopicsSub, internalTopic);
		externalTopicsSub = grow(externalTopicsSub, externalTopic);
		convertersSub = grow(convertersSub,converter);
		qosSub = grow(qosSub, subscriptionQoS);
		
		assert(internalTopicsSub.length == externalTopicsSub.length);
		assert(internalTopicsSub.length == convertersSub.length);
		assert(internalTopicsSub.length == qosSub.length);
		
		return internalTopicsSub.length-1;
	}


	@Override
	public long addTransmission(MsgRuntime<?,?> msgRuntime, CharSequence internalTopic, CharSequence externalTopic) {
		ensureConnected();

		//logger.trace("added subscription to {} in order to transmit out to  ",internalTopic, externalTopic);
		builder.addStartupSubscription(internalTopic, code);
		
		internalTopicsXmit = grow(internalTopicsXmit, internalTopic);
		externalTopicsXmit = grow(externalTopicsXmit, externalTopic);
		convertersXmit = grow(convertersXmit,EgressMQTTStage.copyConverter);
		qosXmit = grow(qosXmit, transmissionFieldQOS);
			
		assert(internalTopicsXmit.length == externalTopicsXmit.length);
		assert(internalTopicsXmit.length == convertersXmit.length);
		assert(internalTopicsXmit.length == qosXmit.length);
		
		return internalTopicsXmit.length-1;
		
	}

	@Override
	public long addTransmission(MsgRuntime<?,?> msgRuntime, CharSequence internalTopic, CharSequence externalTopic, EgressConverter converter) {
		ensureConnected();

		builder.addStartupSubscription(internalTopic, code);
		
		internalTopicsXmit = grow(internalTopicsXmit, internalTopic);
		externalTopicsXmit = grow(externalTopicsXmit, externalTopic);
		convertersXmit = grow(convertersXmit,converter);
		qosXmit = grow(qosXmit, transmissionFieldQOS);
				
		assert(internalTopicsXmit.length == externalTopicsXmit.length);
		assert(internalTopicsXmit.length == convertersXmit.length);
		assert(internalTopicsXmit.length == qosXmit.length);
		
		return internalTopicsXmit.length-1;
	}

	private EgressConverter[] grow(EgressConverter[] converters, EgressConverter converter) {
		
		int i = converters.length;
		EgressConverter[] newArray = new EgressConverter[i+1];
		System.arraycopy(converters, 0, newArray, 0, i);
		newArray[i] = converter;
		return newArray;
	}

	private int[] grow(int[] array, int newItem) {
		
		int i = array.length;
		int[] newArray = new int[i+1];
		System.arraycopy(array, 0, newArray, 0, i);
		newArray[i] = newItem;
		return newArray;
	}
	
	private IngressConverter[] grow(IngressConverter[] converters, IngressConverter converter) {
		
		int i = converters.length;
		IngressConverter[] newArray = new IngressConverter[i+1];
		System.arraycopy(converters, 0, newArray, 0, i);
		newArray[i] = converter;
		return newArray;
	}


	private CharSequence[] grow(CharSequence[] topics, CharSequence topic) {
		
		int i = topics.length;
		CharSequence[] newArray = new CharSequence[i+1];
		System.arraycopy(topics, 0, newArray, 0, i);
		newArray[i] = topic;
		return newArray;
	}

	public void finish(MsgRuntime<?,?> msgRuntime) {
		assert(internalTopicsXmit.length == externalTopicsXmit.length);
		assert(internalTopicsXmit.length == convertersXmit.length);
		
		assert(internalTopicsSub.length == externalTopicsSub.length);
		assert(internalTopicsSub.length == convertersSub.length);		
		
		if (internalTopicsSub.length>0) {
			
			//now publish all our subscription requests
			int i = externalTopicsSub.length;
			while (--i>=0) {			
				PipeWriter.presumeWriteFragment(clientRequest, MQTTClientRequestSchema.MSG_SUBSCRIBE_8);
				PipeWriter.writeInt(clientRequest,MQTTClientRequestSchema.MSG_SUBSCRIBE_8_FIELD_QOS_21, qosSub[i]);
				PipeWriter.writeUTF8(clientRequest,MQTTClientRequestSchema.MSG_SUBSCRIBE_8_FIELD_TOPIC_23, externalTopicsSub[i]);
				PipeWriter.publishWrites(clientRequest);
			}
			
			new IngressMQTTStage(builder.gm, clientResponse, new Pipe<IngressMessages>(builder.pcm.getConfig(IngressMessages.class)), externalTopicsSub, internalTopicsSub, convertersSub);
		} else {
			PipeCleanerStage.newInstance(builder.gm, clientResponse);
		}
		
		if (internalTopicsXmit.length>0) {
			new EgressMQTTStage(builder.gm, msgRuntime.buildPublishPipe(code), clientRequest, internalTopicsXmit, externalTopicsXmit, convertersXmit, qosXmit, transmissionFieldRetain);
		} else {
			PipeNoOp.newInstance(builder.gm, clientRequest);			
		}
	}

	private int activeRow = -1;	
	private final MQTTConfigTransmission transConf = new MQTTConfigTransmission() {
		@Override
		public void setQoS(int qos) {
			qosXmit[activeRow] = qos;
		}
	};
	private final MQTTConfigSubscription subsConf = new MQTTConfigSubscription() {
		@Override
		public void setQoS(int qos) {
			qosSub[activeRow] = qos;
		}
	};

	@Override
	public MQTTConfigTransmission transmissionConfigurator(long id) {
		activeRow = (int)id;
		return transConf;
	}

	@Override
	public MQTTConfigSubscription subscriptionConfigurator(long id) {
		activeRow = (int)id;
		return subsConf;
	}



}
