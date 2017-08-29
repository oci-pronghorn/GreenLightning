package com.ociweb.gl.impl.stage;

import com.ociweb.gl.api.Headable;
import com.ociweb.pronghorn.network.config.HTTPContentType;
import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;
import com.ociweb.pronghorn.network.config.HTTPSpecification;
import com.ociweb.pronghorn.pipe.BlobReader;

public class HeaderTypeCapture implements Headable{

	private HTTPContentType type;
	private HTTPSpecification<?, ?, ?, ?> httpSpec;
	
	public HeaderTypeCapture(HTTPSpecification<?, ?, ?, ?> httpSpec) {
		this.httpSpec = httpSpec;
	}
	
	@Override
	public void read(int id, BlobReader reader) {
		
		short type = reader.readShort();
		if (type<0) {
			this.type = HTTPContentTypeDefaults.UNKNOWN;
		} else {
			this.type = (HTTPContentType)httpSpec.contentTypes[type];
		}
		
	}
	
	public HTTPContentType type() {
		return type;
	}

}
