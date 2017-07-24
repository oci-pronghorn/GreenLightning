package com.ociweb.gl.impl.stage;

import com.ociweb.gl.api.Headable;
import com.ociweb.pronghorn.pipe.BlobReader;

public class HeaderTypeCapture implements Headable{

	private short type;
	
	@Override
	public void read(BlobReader reader) {
		
		type=reader.readShort();
		
	}
	
	public short type() {
		return type;
	}

}
