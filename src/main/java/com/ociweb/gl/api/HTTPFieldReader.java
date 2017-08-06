package com.ociweb.gl.api;

import com.ociweb.pronghorn.network.ServerCoordinator;
import com.ociweb.pronghorn.pipe.MessageSchema;

public interface HTTPFieldReader<S extends MessageSchema<S>> extends FieldReader {

	public static final int BEGINNING_OF_RESPONSE = ServerCoordinator.BEGIN_RESPONSE_MASK;
	public static final int END_OF_RESPONSE = ServerCoordinator.END_RESPONSE_MASK;
	public static final int CLOSE_CONNECTION = ServerCoordinator.CLOSE_CONNECTION_MASK;

	public int headerId(byte[] header);
	
	public int getRevisionId();
	public int getRequestContext();

	public boolean isVerbGet();	
	public boolean isVerbConnect();	
	public boolean isVerbDelete();
	public boolean isVerbHead();
	public boolean isVerbOptions();
	public boolean isVerbPatch();
	public boolean isVerbPost();	
	public boolean isVerbPut();	
	public boolean isVerbTrace();
	
	public long getConnectionId();	
	public long getSequenceCode();
	
	public boolean openHeaderData(byte[] header, Headable headReader);
	
}
