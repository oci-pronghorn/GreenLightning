package com.ociweb.gl.api;

import com.ociweb.pronghorn.pipe.BlobWriter;

public interface NetWritable {

	NetWritable NO_OP = new NetWritable() {
		@Override
		public void write(BlobWriter writer) {
		}		
	};
	
	void write(BlobWriter writer);

}
