package com.ociweb.gl.api;

import com.ociweb.pronghorn.network.config.HTTPContentType;
import com.ociweb.pronghorn.network.config.HTTPHeader;
import com.ociweb.pronghorn.network.config.HTTPRevision;
import com.ociweb.pronghorn.network.config.HTTPSpecification;
import com.ociweb.pronghorn.network.config.HTTPVerb;
import com.ociweb.pronghorn.pipe.ChannelReader;
import com.ociweb.pronghorn.pipe.ChannelWriter;


public class HeaderWriter {

	private static final byte[] BYTES_EOL = "\r\n".getBytes();
	private static final byte[] BYTES_COLON_SPACE = ": ".getBytes();
	private ChannelWriter activeTarget;
	
	HeaderWriter(){		
	}
	
	HeaderWriter target(ChannelWriter activeTarget) {
		this.activeTarget = activeTarget;
		return this;
	}

	/**
	 *
	 * @param header CharSequence to append to activeTarget
	 * @param value CharSequence to append to activeTarget
	 */
	public void write(CharSequence header, CharSequence value) {

			activeTarget.append(header);
			activeTarget.write(BYTES_COLON_SPACE);
			activeTarget.append(value);
			activeTarget.write(BYTES_EOL);

	}
	
	public void writeUTF8(CharSequence header, byte[] value) {	
			activeTarget.append(header);
			activeTarget.write(BYTES_COLON_SPACE);
			activeTarget.write(value);
			activeTarget.write(BYTES_EOL);
	}

	/**
	 *
	 * @param header HTTPHeader to append to activeTarget
	 * @param value CharSequence to append to activeTarget
	 */
	public void write(HTTPHeader header, CharSequence value) {		

			activeTarget.append(header.writingRoot());
			activeTarget.append(value);
			activeTarget.write(BYTES_EOL);

	}

	public void writeUTF8(HTTPHeader header, byte[] value) {

		activeTarget.write(header.rootBytes());//still testing this...
		activeTarget.write(value);
		activeTarget.write(BYTES_EOL);

}


	/**
	 *
	 * @param header HTTPHeader to append to activeTarget
	 * @param value HeaderValue to append HTTPHeader to
	 */
	public void write(HTTPHeader header, HeaderValue value) {		
	
			value.appendTo(activeTarget.append(header.writingRoot()));
			activeTarget.write(BYTES_EOL);

	}

	public void write(HTTPHeader header,
			HTTPSpecification<? extends Enum<? extends HTTPContentType>, ? extends Enum<? extends HTTPRevision>, ? extends Enum<? extends HTTPVerb>, ? extends Enum<? extends HTTPHeader>> spec,
			ChannelReader reader) {
    	
		activeTarget.write(header.rootBytes());
    	header.writeValue(activeTarget, spec, reader);
    	activeTarget.write(BYTES_EOL);
	}
	
}
