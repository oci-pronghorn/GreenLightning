package com.ociweb.gl.api;

public interface NetWritable {

	NetWritable NO_OP = new NetWritable() {
		@Override
		public void write(NetResponseWriter writer) {
		}		
	};
	
	void write(NetResponseWriter writer);

}
