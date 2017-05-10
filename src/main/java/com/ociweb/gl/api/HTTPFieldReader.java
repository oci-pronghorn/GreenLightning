package com.ociweb.gl.api;

import java.util.Optional;

public interface HTTPFieldReader extends FieldReader {

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
