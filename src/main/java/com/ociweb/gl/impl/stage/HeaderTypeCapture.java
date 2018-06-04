package com.ociweb.gl.impl.stage;

import com.ociweb.gl.api.Headable;
import com.ociweb.pronghorn.network.config.HTTPContentType;
import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;
import com.ociweb.pronghorn.network.config.HTTPHeader;
import com.ociweb.pronghorn.network.config.HTTPSpecification;
import com.ociweb.pronghorn.pipe.ChannelReader;

public class HeaderTypeCapture implements Headable {

	private HTTPContentType type;
	private HTTPSpecification<?, ?, ?, ?> httpSpec;
	
	public HeaderTypeCapture(HTTPSpecification<?, ?, ?, ?> httpSpec) {
		this.httpSpec = httpSpec;
	}
	
	@Override
	public void read(HTTPHeader header, ChannelReader reader) {
		
		short type = reader.readShort();
		if ((type<0) || (type>=httpSpec.contentTypes.length)) {
			this.type = HTTPContentTypeDefaults.UNKNOWN;
		} else {
			this.type = (HTTPContentType)httpSpec.contentTypes[type];
		}
		
	}

	/**
	 *
	 * @return type
	 */
	public HTTPContentType type() {
		return type;
	}

	@Override
	public void read(HTTPHeader value, ChannelReader reader, long fieldId) {
		read(value,reader.structured().read(fieldId));		
	}

}
