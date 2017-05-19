package com.ociweb.gl.api;

import java.util.Optional;

import com.ociweb.pronghorn.network.ServerCoordinator;

public interface HTTPFieldReader extends FieldReader {

	public static final int END_OF_RESPONSE = ServerCoordinator.END_RESPONSE_MASK;
	public static final int CLOSE_CONNECTION = ServerCoordinator.CLOSE_CONNECTION_MASK;
	
	public Optional<HeaderReader> openHeaderData(byte[] header);
	public Optional<HeaderReader> openHeaderData(int headerId);
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
}
