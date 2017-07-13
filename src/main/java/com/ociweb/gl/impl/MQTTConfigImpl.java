package com.ociweb.gl.impl;

import com.ociweb.gl.api.BridgeConfig;
import com.ociweb.gl.api.MQTTConfig;
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

public class MQTTConfigImpl extends BridgeConfigImpl implements MQTTConfig {

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
	private short maxInFlight = 10; //add as args
	private int maximumLenghOfVariableLengthFields = 1<<18; //add as args
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
	
	MQTTConfigImpl(CharSequence host, int port, CharSequence clientId, BuilderImpl builder, long rate) {
		this.host = host;
		this.port = port;
		this.clientId = clientId;
		this.builder = builder;
		this.rate = rate;
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
	
	public MQTTConfig keepAliveSeconds(int seconds) {
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
	public MQTTConfig cleanSession(boolean clean) {
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
	
	public MQTTConfig authentication(CharSequence user, CharSequence pass) {
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
	public MQTTConfig subscriptionQoS(int value) {
		subscriptionQoS = value;
		return this;
	}

	@Override
	public MQTTConfig transmissionOoS(int value) {
		transmissionFieldQOS = value;
		return this;
	}

	@Override
	public MQTTConfig transmissionRetain(boolean value) {		
		transmissionFieldRetain = setBitByBoolean(transmissionFieldRetain, value, MQTTEncoder.CONNECT_FLAG_WILL_RETAIN_5 );
		return this;
	}

	public MQTTConfig will(boolean retain, int qos, CharSequence topic, MQTTWritable write ) {
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
			clientRequest = newClientRequestPipe(MQTTClientRequestSchema.instance.newPipeConfig(maxInFlight, maximumLenghOfVariableLengthFields));
			clientRequest.initBuffers();
			
			clientResponse = MQTTClientResponseSchema.instance.newPipe(maxInFlight, maximumLenghOfVariableLengthFields);

			
			MQTTClientGraphBuilder.buildMQTTClientGraph(builder.gm, isTLS, 
					                              maxInFlight, maximumLenghOfVariableLengthFields, 
					                              clientRequest, clientResponse, rate);
			
			//send the broker details
			publishBrokerConfig(clientRequest);
			//send the connect msg
			publishConnect(clientRequest);			
			isImmutable = true;
		}
	}
	
	
	@Override
	public BridgeConfig addSubscription(CharSequence internalTopic, CharSequence externalTopic) {
		ensureConnected();
		
		Pipe<IngressMessages> ingressPipe = IngressMessages.instance.newPipe(10, 1024);

		new IngressMQTTStage(builder.gm, clientResponse, ingressPipe, externalTopic, internalTopic);

		MQTTClientRequestSchema.instance.publishSubscribe(clientRequest, subscriptionQoS, externalTopic);
				
		return this;
	}
	
	@Override
	public BridgeConfig addSubscription(CharSequence internalTopic, CharSequence externalTopic, IngressConverter converter) {
		ensureConnected();
		
		Pipe<IngressMessages> ingressPipe = IngressMessages.instance.newPipe(10, 1024);
		
		new IngressMQTTStage(builder.gm, clientResponse, ingressPipe, externalTopic, internalTopic, converter);
		
		return this;
	}

	private final int code =  System.identityHashCode(this);
	private CharSequence[] internalTopics = new CharSequence[0];
	private CharSequence[] externalTopics = new CharSequence[0];
	private EgressConverter[] converters = new EgressConverter[0];

	@Override
	public BridgeConfig addTransmission(MsgRuntime<?,?> msgRuntime, CharSequence internalTopic, CharSequence externalTopic) {
		ensureConnected();

		builder.addStartupSubscription(internalTopic, code);
		
		internalTopics = grow(internalTopics, internalTopic);
		externalTopics = grow(externalTopics, externalTopic);
		converters = grow(converters,EgressMQTTStage.copyConverter);
			
		return this;
	}

	@Override
	public BridgeConfig addTransmission(MsgRuntime<?,?> msgRuntime, CharSequence internalTopic, CharSequence externalTopic, EgressConverter converter) {
		ensureConnected();

		builder.addStartupSubscription(internalTopic, code);
		
		internalTopics = grow(internalTopics, internalTopic);
		externalTopics = grow(externalTopics, externalTopic);
		converters = grow(converters,converter);
			
		return this;
	}

	private EgressConverter[] grow(EgressConverter[] converters, EgressConverter converter) {
		
		int i = converters.length;
		EgressConverter[] newArray = new EgressConverter[i+1];
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
		new EgressMQTTStage(builder.gm, msgRuntime.buildPublishPipe(code), clientRequest, internalTopics, externalTopics, converters, transmissionFieldQOS, transmissionFieldRetain);
	}

}
