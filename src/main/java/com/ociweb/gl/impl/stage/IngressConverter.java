package com.ociweb.gl.impl.stage;

import com.ociweb.gl.impl.schema.IngressMessages;
import com.ociweb.pronghorn.network.schema.MQTTClientResponseSchema;
import com.ociweb.pronghorn.pipe.DataInputBlobReader;
import com.ociweb.pronghorn.pipe.DataOutputBlobWriter;

public interface IngressConverter {

	void convertData(DataInputBlobReader<MQTTClientResponseSchema> inputStream,
            		 DataOutputBlobWriter<IngressMessages> outputStream);
	
}
