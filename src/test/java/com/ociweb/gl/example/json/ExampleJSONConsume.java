package com.ociweb.gl.example.json;

import com.ociweb.gl.api.Builder;
import com.ociweb.gl.api.GreenApp;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.json.JSONExtractor;
import com.ociweb.json.JSONExtractorCompleted;
import com.ociweb.json.JSONType;

public class ExampleJSONConsume implements GreenApp {

	//TODO: add Name to object.
	
	
	private final JSONExtractorCompleted simpleExtractor = new JSONExtractor()
			.newPath(JSONType.TypeString, false)//set flags for first, last, all, ordered...
			.key("root").key("keyb")
			.completePath("b") //for an array caller will pass visitor to gather all results.
			 //value and index, can return stop bool for data. (index, prim, isnull)
			.newPath(JSONType.TypeInteger, false)//TODO: remove this boolean...
			.key("root").key("keya")
			.completePath("a");

	@Override
	public void declareConfiguration(Builder builder) {

		builder.useHTTP1xServer(8088)
		       .setHost("127.0.0.1") 
		       .useInsecureServer();
		
		builder.defineRoute("/test", simpleExtractor);
				
	}

	@Override
	public void declareBehavior(GreenRuntime runtime) {
		
		runtime.addRestListener(new JSONService(simpleExtractor)).includeAllRoutes();
		
	}
	
	
}
