package com.ociweb.gl.impl.file;

import com.ociweb.gl.impl.PayloadReader;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.RawDataSchema;

public class FilePayloadReader extends PayloadReader<RawDataSchema> {
	
	public FilePayloadReader(Pipe<RawDataSchema> pipe) {
		super(pipe);
	}
	
	
	//TODO:can position and read extracted parts of file name
	//TODO:can position and read extracted internal content.
	
}
