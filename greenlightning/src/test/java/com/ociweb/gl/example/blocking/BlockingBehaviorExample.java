package com.ociweb.gl.example.blocking;

import com.ociweb.gl.api.blocking.BlockingBehavior;
import com.ociweb.pronghorn.pipe.ChannelReader;
import com.ociweb.pronghorn.pipe.ChannelWriter;

public class BlockingBehaviorExample extends BlockingBehavior {

	private long connectionId = -1;
	private long sequenceId = -1;
	private StringBuilder key1Value = new StringBuilder();
	private int key2;
	
	public BlockingBehaviorExample() {
	}
		
	@Override
	public void begin(ChannelReader reader) {
		
		assert(-1 == connectionId);
		assert(-1 == sequenceId);
		
		connectionId = reader.structured().readLong(Field.CONNECTION_ID);
		sequenceId = reader.structured().readLong(Field.SEQUENCE_ID);
		key1Value.setLength(0);
				
	    //TODO: resolve this.. reader.structured().readText(Fields.key1, key1Value);
		key2 = reader.structured().readInt(Field.KEY2);		
		
	}

	@Override
	public void run() throws InterruptedException {
		
		//do something that blocks
		Thread.sleep(1);
		
	}

	@Override
	public void finish(ChannelWriter writer) {
		
		writer.structured().writeLong(Field.CONNECTION_ID, connectionId);
		writer.structured().writeLong(Field.SEQUENCE_ID, sequenceId);
		writer.structured().selectStruct(Struct.DATA); //TODO: need better error when missing.

		connectionId=-1;
		sequenceId=-1;
		
	}

	@Override
	public void timeout(ChannelWriter writer) {
		
		System.out.println("this took too long to run..");
		
	}

}
