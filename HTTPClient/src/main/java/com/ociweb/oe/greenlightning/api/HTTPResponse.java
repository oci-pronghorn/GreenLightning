package com.ociweb.oe.greenlightning.api;

import com.ociweb.gl.api.HTTPResponseListener;
import com.ociweb.gl.api.HTTPResponseReader;
import com.ociweb.gl.api.Payloadable;
import com.ociweb.pronghorn.network.config.HTTPContentType;
import com.ociweb.pronghorn.pipe.BlobReader;

public class HTTPResponse implements HTTPResponseListener {

	@Override
	public boolean responseHTTP(HTTPResponseReader reader) {
		
		System.out.println(" status:"+reader.statusCode());
		System.out.println("   type:"+reader.contentType());

		Payloadable payload = new Payloadable() {
			@Override
			public void read(BlobReader reader) {
				System.out.println(reader.readUTFOfLength(reader.available()));
			}
		};
		boolean hadAbody = reader.openPayloadData(payload );

		
		return true;
	}

}
