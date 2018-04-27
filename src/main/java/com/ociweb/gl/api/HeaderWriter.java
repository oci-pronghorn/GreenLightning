package com.ociweb.gl.api;

import com.ociweb.pronghorn.network.config.HTTPHeader;
import com.ociweb.pronghorn.network.config.HTTPSpecification;
import com.ociweb.pronghorn.pipe.ChannelReader;
import com.ociweb.pronghorn.pipe.ChannelWriter;

import java.io.IOException;

public class HeaderWriter {

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
		try {
			activeTarget.append(header).append(": ").append(value).append("\r\n");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 *
	 * @param header HTTPHeader to append to activeTarget
	 * @param value CharSequence to append to activeTarget
	 */
	public void write(HTTPHeader header, CharSequence value) {		
		try {
			activeTarget.append(header.writingRoot()).append(value).append("\r\n");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 *
	 * @param header
	 * @param httpSpec
	 * @param reader
	 */
	public void write(HTTPHeader header, HTTPSpecification<?,?,?,?> httpSpec, ChannelReader reader) {		
		
			activeTarget.append(header.writingRoot());
			header.writeValue(activeTarget, httpSpec, reader);
			activeTarget.append("\r\n");

	}

	/**
	 *
	 * @param header HTTPHeader to append to activeTarget
	 * @param value HeaderValue to append HTTPHeader to
	 */
	public void write(HTTPHeader header, HeaderValue value) {		
		try {			
			value.appendTo(activeTarget.append(header.writingRoot())).append("\r\n");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
}
