package com.ociweb.gl.api;

import com.ociweb.pronghorn.network.mqtt.MQTTEncoder;
import com.ociweb.pronghorn.network.schema.MQTTClientRequestSchema;
import com.ociweb.pronghorn.pipe.DataOutputBlobWriter;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeWriter;

public class MQTTConfig {

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
	//
	
	public MQTTConfig(CharSequence host, int port, CharSequence clientId) {
		this.host = host;
		this.port = port;
		this.clientId = clientId;
	}
	
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
		keepAliveSeconds = seconds;
		return this;
	}
	
	public MQTTConfig cleanSession(boolean clean) {
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
				
		flags |= MQTTEncoder.CONNECT_FLAG_USERNAME_7;
		flags |= MQTTEncoder.CONNECT_FLAG_PASSWORD_6;		
				
		this.user = user;
		this.pass = pass;
		
		assert(null != user);
		assert(null != pass);
		
		return this;
	}
	
	public MQTTConfig will(boolean retrain, int qos, CharSequence topic, MQTTWritable write ) {
		
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

	
}
