package com.ociweb.gl.api;

import com.ociweb.pronghorn.pipe.BlobWriter;

public interface Writable {

	Writable NO_OP = new Writable() {
		@Override
		public void write(BlobWriter writer) {
		}		
	};
	
	void write(BlobWriter writer); //returns true if we have more data to write.

}
