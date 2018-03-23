package com.ociweb.gl.api;

import java.io.IOException;

import com.ociweb.pronghorn.network.config.HTTPHeader;
import com.ociweb.pronghorn.network.config.HTTPSpecification;
import com.ociweb.pronghorn.pipe.ChannelReader;
import com.ociweb.pronghorn.pipe.ChannelWriter;

public class HeaderWriter {

	private ChannelWriter activeTarget;
	
	HeaderWriter(){		
	}
	
	HeaderWriter target(ChannelWriter activeTarget) {
		this.activeTarget = activeTarget;
		return this;
	}

	public void write(CharSequence header, CharSequence value) {
		try {
			activeTarget.append(header).append(": ").append(value).append("\r\n");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	public void write(HTTPHeader header, CharSequence value) {		
		try {
			activeTarget.append(header.writingRoot()).append(value).append("\r\n");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	public void write(HTTPHeader header, HTTPSpecification<?,?,?,?> httpSpec, ChannelReader reader) {		
		
			activeTarget.append(header.writingRoot());
			header.writeValue(activeTarget, httpSpec, reader);
			activeTarget.append("\r\n");

	}
	
	public void write(HTTPHeader header, HeaderValue value) {		
		try {			
			value.appendTo(activeTarget.append(header.writingRoot())).append("\r\n");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
}
