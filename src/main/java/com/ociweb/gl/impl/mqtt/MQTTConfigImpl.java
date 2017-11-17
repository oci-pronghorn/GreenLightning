package com.ociweb.gl.impl.mqtt;

import com.ociweb.gl.api.*;
import com.ociweb.pronghorn.network.TLSCertificates;
import com.ociweb.pronghorn.pipe.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.gl.impl.BridgeConfigImpl;
import com.ociweb.gl.impl.BuilderImpl;
import com.ociweb.gl.impl.schema.IngressMessages;
import com.ociweb.gl.impl.stage.EgressConverter;
import com.ociweb.gl.impl.stage.EgressMQTTStage;
import com.ociweb.gl.impl.stage.IngressConverter;
import com.ociweb.gl.impl.stage.IngressMQTTStage;
import com.ociweb.pronghorn.network.mqtt.MQTTClientGraphBuilder;
import com.ociweb.pronghorn.network.mqtt.MQTTEncoder;
import com.ociweb.pronghorn.network.schema.MQTTClientRequestSchema;
import com.ociweb.pronghorn.network.schema.MQTTClientResponseSchema;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;
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
	private CharSequence lastWillTopic = null;
	private CharSequence connectionFeedbackTopic;
	private Writable lastWillPayload = null;
	//
	private int flags;
	private TLSCertificates certificates;
	
	private final short maxInFlight;
	private int maximumLenghOfVariableLengthFields;
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
	
	public MQTTConfigImpl(CharSequence host, int port, CharSequence clientId,
			       BuilderImpl builder, long rate, 
			       short maxInFlight, int maxMessageLength) {
		
		this.certificates = certificates;
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
	    PipeWriter.writeUTF8(output,MQTTClientRequestSchema.MSG_CONNECT_1_FIELD_WILLTOPIC_31, (CharSequence) lastWillTopic);
	    
	    DataOutputBlobWriter<MQTTClientRequestSchema> writer = PipeWriter.outputStream(output);
	    DataOutputBlobWriter.openField(writer);	
	    if(null!= lastWillPayload) {
	    	lastWillPayload.write((MQTTWriter)writer);
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

	public MQTTBridge useTLS() {
		return useTLS(TLSCertificates.defaultCerts);
	}

	public MQTTBridge useTLS(TLSCertificates certificates) {
		if (isImmutable) {
			throw new UnsupportedOperationException("Mutations must happen earlier.");
		}
		assert(null != certificates);
		this.certificates = certificates;
		this.maximumLenghOfVariableLengthFields = Math.max(this.maximumLenghOfVariableLengthFields, 1<<15);
		return this;
	}
	
	public MQTTBridge authentication(CharSequence user, CharSequence pass) {
		return this.authentication(user, pass, TLSCertificates.defaultCerts);
	}

	public MQTTBridge authentication(CharSequence user, CharSequence pass, TLSCertificates certificates) {
		if (isImmutable) {
			throw new UnsupportedOperationException("Mutations must happen earlier.");
		}
		flags |= MQTTEncoder.CONNECT_FLAG_USERNAME_7;
		flags |= MQTTEncoder.CONNECT_FLAG_PASSWORD_6;

		this.user = user;
		this.pass = pass;

		assert(null != user);
		assert(null != pass);
		assert(null != certificates);

		return this;
	}

	@Override
	public MQTTBridge subscriptionQoS(MQTTQoS qos) {
		subscriptionQoS = qos.getSpecification();
		return this;
	}

	@Override
	public MQTTBridge transmissionOoS(MQTTQoS qos) {
		transmissionFieldQOS = qos.getSpecification();
		return this;
	}

	@Override
	public MQTTBridge transmissionRetain(boolean value) {		
		transmissionFieldRetain = setBitByBoolean(transmissionFieldRetain, value, MQTTEncoder.CONNECT_FLAG_WILL_RETAIN_5 );
		return this;
	}

	@Override
	public MQTTBridge lastWill(CharSequence topic, boolean retain, MQTTQoS qos, Writable payload) {
		if (isImmutable) {
			throw new UnsupportedOperationException("Mutations must happen earlier.");
		}
		assert(null!=topic);

		flags |= MQTTEncoder.CONNECT_FLAG_WILL_FLAG_2;
		if (retain) {
			flags |= MQTTEncoder.CONNECT_FLAG_WILL_RETAIN_5;
		}
		byte qosFlag = (byte) (qos.getSpecification() << 3);
		flags |= qosFlag;

		this.lastWillTopic = topic;
		this.lastWillPayload = payload;

		return this;
	}


	public MQTTBridge connectionFeedbackTopic(CharSequence connectFeedbackTopic) {
		this.connectionFeedbackTopic = connectFeedbackTopic;
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

			final byte totalConnectionsInBits = 2; //only 4 brokers
			final short maxPartialResponses = 1;
			String user = null;
			String pass = null;

			MQTTClientGraphBuilder.buildMQTTClientGraph(builder.gm, certificates,
					                              maxInFlight,
					                              maximumLenghOfVariableLengthFields, 
					                              clientRequest, clientResponse, rate, 
					                              totalConnectionsInBits,
					                              maxPartialResponses,
					                              user,pass);
			
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
	private int[] retainXmit = new int[0];
	
	
	
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
		retainXmit = grow(retainXmit, transmissionFieldRetain);		
		
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

			IngressMQTTStage stage = new IngressMQTTStage(builder.gm, clientResponse, new Pipe<IngressMessages>(builder.pcm.getConfig(IngressMessages.class)), externalTopicsSub, internalTopicsSub, convertersSub, connectionFeedbackTopic);
			GraphManager.addNota(builder.gm, GraphManager.DOT_BACKGROUND, MQTTClientGraphBuilder.BACKGROUND_COLOR, stage);

		} else {
			PipeCleanerStage.newInstance(builder.gm, clientResponse);
		}
		
		if (internalTopicsXmit.length>0) {
			EgressMQTTStage stage = new EgressMQTTStage(builder.gm, msgRuntime.buildPublishPipe(code), clientRequest, internalTopicsXmit, externalTopicsXmit, convertersXmit, qosXmit, retainXmit);
			GraphManager.addNota(builder.gm, GraphManager.DOT_BACKGROUND, MQTTClientGraphBuilder.BACKGROUND_COLOR, stage);
		} else {
			PipeNoOp.newInstance(builder.gm, clientRequest);			
		}
	}

	private int activeRow = -1;	
	private final MQTTConfigTransmission transConf = new MQTTConfigTransmission() {
		@Override
		public MQTTConfigTransmission setQoS(MQTTQoS qos) {
			qosXmit[activeRow] = qos.getSpecification();
			return transConf;
		}

		@Override
		public MQTTConfigTransmission setRetain(boolean retain) {
			retainXmit[activeRow] = retain?1:0;
			return transConf;
		}
	};
	private final MQTTConfigSubscription subsConf = new MQTTConfigSubscription() {
		@Override
		public void setQoS(MQTTQoS qos) {
			qosSub[activeRow] = qos.getSpecification();
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
