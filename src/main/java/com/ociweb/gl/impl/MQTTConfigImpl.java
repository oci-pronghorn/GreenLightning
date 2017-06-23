package com.ociweb.gl.impl;

import com.ociweb.gl.api.BridgeConfig;
import com.ociweb.gl.api.MQTTConfig;
import com.ociweb.gl.api.MQTTWritable;
import com.ociweb.gl.api.MQTTWriter;
import com.ociweb.pronghorn.network.NetGraphBuilder;
import com.ociweb.pronghorn.network.mqtt.MQTTClientGraphBuilder;
import com.ociweb.pronghorn.network.mqtt.MQTTEncoder;
import com.ociweb.pronghorn.network.schema.MQTTClientRequestSchema;
import com.ociweb.pronghorn.network.schema.MQTTClientResponseSchema;
import com.ociweb.pronghorn.pipe.DataOutputBlobWriter;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeWriter;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;

public class MQTTConfigImpl extends BridgeConfigImpl implements MQTTConfig {

	private final CharSequence host;
	private final int port;
	private final CharSequence clientId;
	private int keepAliveSeconds = 10; //default
	//
	private CharSequence user = null;
	private CharSequence pass = null;
	//
	private CharSequence willTopic = null;
	private MQTTWritable willPayload = null;
	//
	private int flags;
	private boolean isTLS;
	private short maxInFlight;
	private int maximumLenghOfVariableLengthFields = 1<<18;
	//
	private final BuilderImpl builder;
	//
	private boolean isImmutable;
	
	MQTTConfigImpl(CharSequence host, int port, CharSequence clientId, BuilderImpl builder) {
		this.host = host;
		this.port = port;
		this.clientId = clientId;
		this.builder = builder;
	}
	
	private void build(GraphManager gm) {
		
		Pipe<MQTTClientRequestSchema> clientRequest = 
				MQTTClientRequestSchema.instance.newPipe(maxInFlight, maximumLenghOfVariableLengthFields);
		
		Pipe<MQTTClientResponseSchema> clientResponse = 
				MQTTClientResponseSchema.instance.newPipe(maxInFlight, maximumLenghOfVariableLengthFields);
		
		MQTTClientGraphBuilder.buildMQTTClientGraph(gm, isTLS, maxInFlight, maximumLenghOfVariableLengthFields, clientRequest, clientResponse);
	
	}
	
	//send on construction, do not save		
	public boolean publishBrokerConfig(Pipe<MQTTClientRequestSchema> output) {
		if (PipeWriter.tryWriteFragment(output, MQTTClientRequestSchema.MSG_BROKERCONFIG_100)) {
			
		    PipeWriter.writeUTF8(output,MQTTClientRequestSchema.MSG_BROKERCONFIG_100_FIELD_HOST_26, (CharSequence) host);
		    PipeWriter.writeInt(output,MQTTClientRequestSchema.MSG_BROKERCONFIG_100_FIELD_PORT_27, port);
		    PipeWriter.publishWrites(output);
		    return true;
		
		} else {
			return false;
		}
				
	}
	
	//send upon complete construction
	public boolean publishConnect(Pipe<MQTTClientRequestSchema> output) {
		if (PipeWriter.tryWriteFragment(output, MQTTClientRequestSchema.MSG_CONNECT_1)) {
			
		    PipeWriter.writeInt(output,MQTTClientRequestSchema.MSG_CONNECT_1_FIELD_KEEPALIVESEC_28, keepAliveSeconds);
		    PipeWriter.writeInt(output,MQTTClientRequestSchema.MSG_CONNECT_1_FIELD_FLAGS_29, flags);
		    PipeWriter.writeUTF8(output,MQTTClientRequestSchema.MSG_CONNECT_1_FIELD_CLIENTID_30, (CharSequence) clientId);
		    PipeWriter.writeUTF8(output,MQTTClientRequestSchema.MSG_CONNECT_1_FIELD_WILLTOPIC_31, (CharSequence) willTopic);
		    
		    DataOutputBlobWriter<MQTTClientRequestSchema> writer = PipeWriter.outputStream(output);
		    DataOutputBlobWriter.openField(writer);		    
		    willPayload.write((MQTTWriter)writer);
		    DataOutputBlobWriter.closeHighLevelField(writer, MQTTClientRequestSchema.MSG_CONNECT_1_FIELD_WILLPAYLOAD_32);
		    

		    PipeWriter.writeUTF8(output,MQTTClientRequestSchema.MSG_CONNECT_1_FIELD_USER_33, (CharSequence) user);
		    PipeWriter.writeUTF8(output,MQTTClientRequestSchema.MSG_CONNECT_1_FIELD_PASS_34, (CharSequence) pass);
		    PipeWriter.publishWrites(output);
		    
		    return true;
		} else {
			return false;
		}
				
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
		setBitByBoolean(clean, MQTTEncoder.CONNECT_FLAG_CLEAN_SESSION_1);
		return this;
	}

	private void setBitByBoolean(boolean clean, int bit) {
		if (clean) {
			flags = flags|bit;
		} else {
			flags = (~flags)&bit;
		}
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
	
	public MQTTConfig will(boolean retrain, int qos, CharSequence topic, MQTTWritable write ) {
		if (isImmutable) {
			throw new UnsupportedOperationException("Mutations must happen earlier.");
		}
		assert(null!=topic);
		assert(null!=write);
		
		setBitByBoolean(retrain, MQTTEncoder.CONNECT_FLAG_WILL_RETAIN_5);
		
		setBitByBoolean((2&qos)!=0, MQTTEncoder.CONNECT_FLAG_WILL_QOS_3);
		setBitByBoolean((1&qos)!=0, MQTTEncoder.CONNECT_FLAG_WILL_QOS_4);
		
		flags |= MQTTEncoder.CONNECT_FLAG_WILL_FLAG_2;
		
		this.willTopic = topic;
		this.willPayload = write;
		
		return this;
	}

	private void ensureConnected() {
		if (isImmutable) {
			return;
		} else {
			
			//send the connect msg
			
			
			isImmutable = true;
		}
		
	}
	
	
	@Override
	public BridgeConfig addSubscription(CharSequence internalTopic, CharSequence externalTopic) {
		ensureConnected();
		
		// TODO need place to hook transform
		//send subscriptions to Pipe<MQTTClientRequestSchema> clientRequest in behavior??
		
		//create new ingressMessage pipe for this stage, it will be consumed later
		//create stage to take data from MQTT Response pipe and publish to where?
		//TODO: build new schema for just messages with payload..
		
		return this;
	}

	@Override
	public BridgeConfig addTransmission(CharSequence internalTopic, CharSequence externalTopic) {
		ensureConnected();
		
		// TODO need place to hook transform
//		builder.addStartupSubscription(topic, System.identityHashCode(this));
//		//Pipe msgRuntime.buildPublishPipe(this) //data from the pipe is to be routed to MQTT
		
		//create stage to consume this pipe and write to MQTT output
		// must take transform
		
		return this;
	}

	
	
}
