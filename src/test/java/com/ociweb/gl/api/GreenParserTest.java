package com.ociweb.gl.api;

import static com.ociweb.gl.api.GreenParserTest.FieldType.*;
import static org.junit.Assert.*;

import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.pronghorn.pipe.BlobReader;
import com.ociweb.pronghorn.pipe.DataInputBlobReader;
import com.ociweb.pronghorn.pipe.DataOutputBlobWriter;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.RawDataSchema;

import java.text.DecimalFormat;
import java.text.NumberFormat;

interface MyConsumer<T> {
	void accept(T t);
}

public class GreenParserTest {

	private final static Logger logger = LoggerFactory.getLogger(GreenParserTest.class);

	@Test
	public void simpleTest() {
				
		GreenTokenizer gt = new GreenTokenMap()
				              .add(1, "moe")
				              .add(2, "larry")
				              .add(3, "curly")
				              .add(0, "shemp")
				              .newTokenizer();
		
		assertEquals("larry not found",2, gt.tokenize("larry"));
		assertEquals(1, gt.tokenize("moe"));
		assertEquals(0, gt.tokenize("shemp"));
		assertEquals(3, gt.tokenize("curly"));                                              		              
		assertEquals(-1, gt.tokenize("bob"));
	    				              		
		////////////////
		//This is an example of how to use tokens in a switch
		/////////////////
		String value = "larry";
		switch ((int)gt.tokenize(value)) {	 //NOTE: cast to int is required	
			case 2:
				//this is larry
				break;
			default:
				fail("larry was not found"); //not larry		
		}
		
	}
	
	
	@Test
	public void extractionsTest() {
				
		GreenTokenizer gt = new GreenTokenMap()
				              .add(1, "age: %i\n")
				              .add(2, "name: %b\n") //note %b MUST be followed by stop char
				              .add(3, "speed: %i%.\n") //note this counts as 2 fields
				              .add(0, "\n")
				              .newTokenizer();
				
		
		assertEquals(2, gt.tokenize("name: bob\n"));
		assertEquals(-1, gt.tokenize("name: bobxx"));
		
		assertEquals(1, gt.tokenize("age: 23\n"));
		assertEquals(-1, gt.tokenize("age: sf\n"));
		
		assertEquals(3, gt.tokenize("speed: 2.3\n"));                                              		          
		assertEquals(-1, gt.tokenize("speed: 2.z3\n"));                                              		          
				
		assertEquals(0, gt.tokenize("\n"));
		assertEquals(-1, gt.tokenize("bob"));
	    				              		
		////////////////
		//This is an exmple of how to use tokens in a switch
		/////////////////
		switch ((int)gt.tokenize("name: bob\n")) {	 //NOTE: cast to int is required	
			case 2:				
				assertEquals("bob", gt.extractedString(0));				
				break;
			default:
				fail(); //not a name		
		}
				
		switch ((int)gt.tokenize("age: 56\n")) {	 //NOTE: cast to int is required	
		case 1:				
			assertEquals(56, gt.extractedLong(0));			
			break;
		default:
			fail(); //not a name		
	    }
				
		switch ((int)gt.tokenize("speed: 4.5\n")) {	 //NOTE: cast to int is required	
		case 3:	
			assertEquals(4.5d, gt.extractedDouble(0),.001);	//NOTE: this method assumes 2 fields make up the value		
			break;
		default:
			fail(); //not a name	
		}		
		
	}
		
	
	@Test
	public void simpleReaderTest() {
				
		GreenReader gr = new GreenTokenMap()
				              .add(1, "moe")
				              .add(2, "larry")
				              .add(3, "curly")
				              .add(0, "shemp")
				              .newReader();
		
		
		BlobReader testToRead = generateSimpleDataToTest();
		
		///////
		//example consumer code starts here
		///////
		
		boolean foundLarry = false;
		boolean foundShemp = false;
		
		gr.beginRead(testToRead);
		while (gr.hasMore()) {
			
			long token = gr.readToken();
			
			switch ((int)token) {
				case 0: //this is a token id
					foundShemp = true;			    
					break;
				case 2: //this is a token id
					foundLarry = true;
					break;
				case -1:
					//unknown
					gr.skipByte();
				    break;
				case 1:
				case 3:
					//ignore
					break;
			}
		}
		
		assertTrue("can not find larry",foundLarry);
		assertTrue("can not find shemp",foundShemp);
		
	}


	private BlobReader generateSimpleDataToTest() {
		
		Pipe<RawDataSchema> p = RawDataSchema.instance.newPipe(10, 300);
		p.initBuffers();		
		int size = Pipe.addMsgIdx(p, 0);
		DataOutputBlobWriter<?> stream = Pipe.outputStream(p);		
		stream.openField();
		
		///Here is the data
		
		stream.append("larry");
		stream.append("shemp");
		
		//Done with the data
		
		stream.closeLowLevelField();
		Pipe.confirmLowLevelWrite(p,size);
		Pipe.publishWrites(p);
		
		Pipe.takeMsgIdx(p);
		DataInputBlobReader<?> streamOut = Pipe.inputStream(p);
		streamOut.openLowLevelAPIField();
		return streamOut;
	}
	
	@Test
	public void extractionsReaderTest() {
				
		GreenReader gr = new GreenTokenMap()
				              .add(1, "age: %i\n")
				              .add(2, "name: %b, %b\n") //note %b MUST be followed by stop char
				              .add(3, "speed: %i%.\n") //note this counts as 2 fields
				              .add(0, "\n")
				              .newReader();
				
		BlobReader testToRead = generateExtractionDataToTest(new MyConsumer<DataOutputBlobWriter<?>>() {
			@Override
			public void accept(DataOutputBlobWriter<?> dataOutputBlobWriter) {
				defaultStreamAppend(dataOutputBlobWriter);
			}
		});

		long age = Integer.MIN_VALUE;
		StringBuilder name = new StringBuilder();
		double speed = -1; 
				
		gr.beginRead(testToRead);
		while (gr.hasMore()) {
			
			long token = gr.readToken();
			
			logger.trace("extractionsReader token: {} ", token);
			
			switch ((int)token) {
				case 1: 
					age = gr.extractedLong(0);
				break;
				
				case 2:
					gr.copyExtractedUTF8ToAppendable(1, name);
					name.append(" ");
					gr.copyExtractedUTF8ToAppendable(0, name);
				break;
				
				case 3:
					speed = gr.extractedDouble(0);
			    break;			    
				case 0:
					//skips known white space
					break;
				default:
					//skip unknown data the extra return	
					gr.skipByte();
			}
		}
		
		assertEquals(42, age);
		assertEquals("billy bob", name.toString());
		assertEquals(7.2d, speed, .00001);
						
	}

	private static void defaultStreamAppend(DataOutputBlobWriter<?> stream) {
		stream.append("name: bob, billy\n");
		stream.append("\n");//white space
		stream.append("bad-data");//to be ignored
		stream.append("age: 42\n");
		stream.append("speed: 7.2\n");
	}
	
	private BlobReader generateExtractionDataToTest(MyConsumer<DataOutputBlobWriter<?>> appender) {
		
		Pipe<RawDataSchema> p = RawDataSchema.instance.newPipe(10, 300);
		p.initBuffers();		
		int size = Pipe.addMsgIdx(p, 0);
		DataOutputBlobWriter<?> stream = Pipe.outputStream(p);		
		stream.openField();
		
		///Here is the data
		appender.accept(stream);

		//Done with the data
		int lenWritten = stream.length();
		
		stream.closeLowLevelField();
		Pipe.confirmLowLevelWrite(p,size);
		Pipe.publishWrites(p);
		
		Pipe.takeMsgIdx(p);
		DataInputBlobReader<?> streamOut = Pipe.inputStream(p);
		streamOut.openLowLevelAPIField();
		
		assertEquals(lenWritten, streamOut.available());
		
		return streamOut;
	}

	public enum FieldType {
		integer,
		string,
		floatingPoint,
		int64
	}

	public static final FieldType[] types = new FieldType[] {
			integer,
			integer,
			string,
			integer,
			integer,
			floatingPoint,
			int64,
			int64,
			string,
			string,
			string,
	};

	final static String[] patterns = new String[] {
			"st%u",
			"sn%u",
			"pn\"%b\"",
			"cl%u",
			"cc%u",
			"pp%i",
			"fd%u",
			"sd%u",
			"pf\"%b\"",
			"ld\"%b\"",
			"in\"%b\"",
	};

	static GreenTokenMap buildTokenizerMap() {
		GreenTokenMap map = new GreenTokenMap();
		for (int i = 0; i < patterns.length; i++) {
			map = map.add(i, patterns[i]);
		}
		return map;
	}

	final static String complexData = "st2sn1020pn\"NX-DCV-SM-BLU-2-V0-L0-S0-00\"cl637512101cc1pp36.3833pf\"N\"ld\"N\"in\"A\"fd61423765200000sd61426357200000";

	private static void complexStreamAppend(DataOutputBlobWriter<?> stream) {
		stream.append(complexData);
	}

	@Test
	@Ignore
	public void complexStringTest() {
		NumberFormat formatter = new DecimalFormat("#0.0000");
		final GreenReader reader = buildTokenizerMap().newReader();
		BlobReader testToRead = generateExtractionDataToTest(new MyConsumer<DataOutputBlobWriter<?>>() {
			@Override
			public void accept(DataOutputBlobWriter<?> dataOutputBlobWriter) {
				complexStreamAppend(dataOutputBlobWriter);
			}
		});
		reader.beginRead(testToRead);
		StringBuilder rebuild = new StringBuilder();
		while (reader.hasMore()) {
			int parsedId = (int)reader.readToken();
			if (parsedId == -1) {
				reader.skipByte();
			}
			else {
				final FieldType fieldType = types[parsedId];
				final String key = patterns[parsedId].substring(0, 2);
				rebuild.append(key);
				switch (fieldType) {
					case integer: {
						int value = (int) reader.extractedLong(0);
						rebuild.append(value);
						break;
					}
					case int64: {
						long value = reader.extractedLong(0);
						rebuild.append(value);
						break;
					}
					case string: {
						StringBuilder value = new StringBuilder();
						reader.copyExtractedUTF8ToAppendable(0, value);
						rebuild.append("\"");
						rebuild.append(value);
						rebuild.append("\"");
						break;
					}
					case floatingPoint: {
						double value = reader.extractedDouble(0);
						rebuild.append(formatter.format(value));
						break;
					}
				}
			}
		}
		assertEquals(complexData, rebuild.toString());
	}
}
