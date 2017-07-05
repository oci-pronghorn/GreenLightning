package com.ociweb.gl.impl.stage;

import com.ociweb.gl.impl.schema.MessageSubscription;
import com.ociweb.pronghorn.network.schema.MQTTClientRequestSchema;
import com.ociweb.pronghorn.pipe.DataInputBlobReader;
import com.ociweb.pronghorn.pipe.DataOutputBlobWriter;

public interface EgressConverter {

	void convert(DataInputBlobReader<MessageSubscription> inputStream,
			     DataOutputBlobWriter<MQTTClientRequestSchema> outputStream);

}
