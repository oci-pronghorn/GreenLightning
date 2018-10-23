package com.ociweb.gl.impl.blocking;

import com.ociweb.gl.api.PubSubListener;
import com.ociweb.pronghorn.pipe.ChannelReader;
import com.ociweb.pronghorn.pipe.DataInputBlobReader;
import com.ociweb.pronghorn.pipe.DataOutputBlobWriter;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.RawDataSchema;
import com.ociweb.pronghorn.pipe.StructuredReader;

public class BalanceBlockingBehavior implements PubSubListener {

	private Pipe<RawDataSchema>[] toBlockingWork;
	private Object fieldIdAssoc;

	public BalanceBlockingBehavior(Pipe<RawDataSchema>[] toBlockingWork, Object fieldIdAssoc) {
		this.toBlockingWork = toBlockingWork;
		this.fieldIdAssoc = fieldIdAssoc;		
	}
	
	@Override
	public boolean message(CharSequence topic, ChannelReader payload) {//this allows for normal subscription usages
	
	    //must go back here for the data copy after we read the field
		final int startPos = payload.absolutePosition();
				
		Pipe<RawDataSchema> target = null;
		StructuredReader reader = payload.structured();
		if (!reader.hasAttachedObject(fieldIdAssoc)) {

			target = toBlockingWork[0];//default
		} else {
			long readLong =  reader.readLong(fieldIdAssoc) >> 1; //since SocketReaders are always grouped by 2
			int targetIdx = (int)(readLong%toBlockingWork.length);
	
			target = toBlockingWork[targetIdx];
		}
		
		if (Pipe.hasRoomForWrite(target)) {
		
			payload.absolutePosition(startPos);//we already positioned and consumed the value so reposition now.
			
			int size = Pipe.addMsgIdx(target, RawDataSchema.MSG_CHUNKEDSTREAM_1);
			
			DataOutputBlobWriter<RawDataSchema> writer = Pipe.openOutputStream(target);	

			payload.readInto(writer, payload.available()); //copies all the field data 
			
			//TODO: this is too complext to get right..
			boolean copiedIdx = ((DataInputBlobReader)payload).readFromEndInto(writer); //ciopy index and struct			
			int type = DataInputBlobReader.getStructType((DataInputBlobReader)payload);
			DataOutputBlobWriter.commitBackData(writer,	type);			
			
			int consumed = writer.closeLowLevelField();

			Pipe.confirmLowLevelWrite(target, size);
			Pipe.publishWrites(target);		

			return true;
		} else {
			return false;
		}
	}
}
