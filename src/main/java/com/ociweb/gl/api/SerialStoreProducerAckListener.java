package com.ociweb.gl.api;

public interface SerialStoreProducerAckListener {

	boolean producerAck(int storeId, long value);

}
