package com.ociweb.gl.api;

import com.ociweb.pronghorn.pipe.MessageSchema;

public interface HTTPFieldReader<S extends MessageSchema<S>> {

	public int getRevisionId();

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
