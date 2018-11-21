package com.ociweb.gl.api;

public interface PathWatchConfig {
	
	PathWatchConfig customWatchRate(long rate);
	PathWatchConfig customEvents(int enumCreateModifyDelete);
	
	//3 ways of watching for a file.
	FileJSONWatchConfig parseJSON(); // extends ExtractedJSONFields<ExtractedJSONFieldsForRoute>
	//FileWatchConfig parseCSV(); //CSV
	FileWatchConfig filePattern(String pattern);
	
	//TODO revist the sql to take field ids and auto post.
	//TODO need JDBC example as well.
	//TODO need status counters and failure bucket..
	
	
}
