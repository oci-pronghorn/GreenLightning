package com.ociweb.gl.example.json;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.gl.api.HTTPRequestReader;
import com.ociweb.gl.api.Payloadable;
import com.ociweb.json.JSONExtractorCompleted;
import com.ociweb.pronghorn.pipe.ChannelReader;
import com.ociweb.pronghorn.util.parse.JSONReader;

public class JSONService implements com.ociweb.gl.api.RestListener {

	private static final Logger logger = LoggerFactory.getLogger(JSONService.class);
	private final JSONReader jsonReader;
		
	public JSONService(JSONExtractorCompleted simpleExtractor) {
		this.jsonReader = simpleExtractor.reader();
	}

	@Override
	public boolean restRequest(HTTPRequestReader request) {
		
		//TODO: must not be zero..
		logger.info("got call to readRequest size:"+request.available());
		
		//pull in args from the URL
		//request.getInt("my field".getBytes())
		
		//pull in headers
		//request.openHeaderData("my header".getBytes(), headReader);
		
		
		Payloadable reader = new Payloadable() {

			@Override
			public void read(ChannelReader reader) {
				
				//TODO: add index data to the end to find these??
				//simpleExtractor.getLong("fieldName".getBytes(), reader);
				//must call in order, can always start at beginning.
				
				
				//lookup the field name to find the index for this call
				//this requires us to add more index data??
				
				
				System.err.println("bytes of data "+reader.available());
				
				
				//String x = reader.readUTFOfLength(reader.available());
				//System.err.println(x);
				
				System.out.println("text :"
						+jsonReader.getText("b".getBytes(), reader, new StringBuilder()).toString());
				
				
				System.out.println("value :"
				        +jsonReader.getLong("a".getBytes(), reader));
				
				//long nullMask = reader.readPackedLong();
				//System.out.println("null mask "+nullMask);
				//System.out.println("text :"+reader.readUTFOfLength(reader.readPackedInt()));
				//System.out.println("value :"+reader.readPackedLong());
				
				
			}
			
		};
		
		request.openPayloadData(reader );
		
		return true;
	}

}
