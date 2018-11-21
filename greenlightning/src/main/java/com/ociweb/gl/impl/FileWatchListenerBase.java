package com.ociweb.gl.impl;

import com.ociweb.gl.impl.file.FilePayloadReader;

public interface FileWatchListenerBase extends FileWatchMethodListenerBase{

	//TODO: before this point we have declare decryption, JSONParse, CSV pare, props parse, or raw..
	 boolean fileEvent(FilePayloadReader reader); //delete and create and modify??
	
}
