package com.ociweb.gl.example.blocking;

import com.ociweb.gl.api.blocking.BlockingBehavior;
import com.ociweb.pronghorn.pipe.ChannelReader;
import com.ociweb.pronghorn.pipe.ChannelWriter;

public class BlockingBehaviorExample extends BlockingBehavior {

	private long connectionId = -1;
	private long sequenceId = -1;
	private StringBuilder key1Value = new StringBuilder();
	private int key2;
	private final int structId;
	
	public BlockingBehaviorExample(int structId) {
		this.structId = structId;
	}
		
	@Override
	public void begin(ChannelReader reader) {
		
		assert(-1 == connectionId);
		assert(-1 == sequenceId);
		
		connectionId = reader.structured().readLong(Fields.connectionId);
		sequenceId = reader.structured().readLong(Fields.sequenceId);
		key1Value.setLength(0);
	//	reader.structured().readText(Fields.key1, key1Value);
		key2 = reader.structured().readInt(Fields.key2);		
		
	}

	@Override
	public void run() throws InterruptedException {
		
		//do something that blocks
		Thread.sleep(1);
		
	}

	@Override
	public void finish(ChannelWriter writer) {
		
		writer.structured().writeLong(Fields.connectionId, connectionId);
		writer.structured().writeLong(Fields.sequenceId, sequenceId);
		writer.structured().selectStruct(structId); //TODO: need better error when missing.

		connectionId=-1;
		sequenceId=-1;
		
	}

	@Override
	public void timeout(ChannelWriter writer) {
		throw new RuntimeException("not yet supported");
		
		//finish(writer);
	}

}
