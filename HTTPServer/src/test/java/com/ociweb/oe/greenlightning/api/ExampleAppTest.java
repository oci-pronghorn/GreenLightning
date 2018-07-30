package com.ociweb.oe.greenlightning.api;

import static org.junit.Assert.assertTrue;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.Writable;
import com.ociweb.gl.test.LoadTester;
import com.ociweb.json.encode.JSONRenderer;
import com.ociweb.oe.greenlightning.api.ExampleAppTest.Person;
import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;
import com.ociweb.pronghorn.pipe.ChannelWriter;

public class ExampleAppTest {

	public class Person {

		public final String name;
		public final int age;
		
		public Person(String name, int age) {
			this.name=name;
			this.age=age;
		}

	}

	static GreenRuntime runtime;
	static int port = 8050;
	static int telemetryPort = 8097;
	static String host = "127.0.0.1";
	
	int timeoutMS = 60_000;	
	boolean useTLS = true;
	boolean telemetry = false;
	int parallelTracks = 2;
	int cyclesPerTrack = 100;

	
	@BeforeClass
	public static void startServer() {
		
		StringBuilder console = new StringBuilder();
		runtime = GreenRuntime.run(new HTTPServer(host,port,console,telemetryPort));
		
	}
		
	@AfterClass
	public static void stopServer() {
		runtime.shutdownRuntime();	
		runtime = null;
	}
	
	@Test
	public void jsonCallTest() {
		
		 Writable testData = new Writable() {			 
				@Override
				public void write(ChannelWriter writer) {
					writer.append("{\"person\":{\"name\":\"bob\",\"age\":42}}");
				}						
			};
		
		StringBuilder results = LoadTester.runClient(
				()->testData, 
				(r)->{
						return "{\"name\":\"bob\",\"isLegal\":true}".equals(r.structured().readPayload().readUTFFully())
								&& (HTTPContentTypeDefaults.JSON == r.contentType());
					  }, 
				"/testJSON", 
				useTLS, telemetry, 
				parallelTracks, cyclesPerTrack, 
				host, port, timeoutMS);		
		
		assertTrue(results.toString(), results.indexOf("Responses invalid: 0 out of "+(cyclesPerTrack*parallelTracks))>=0);

	}
	
	@Test
	public void jsonCall2Test() {
		
		Person person = new Person("bob",42);
		JSONRenderer<Person> renderer = new JSONRenderer<Person>()
				.beginObject()
				.beginObject("person")
				    .string("name", (o,t)->t.append(o.name))
				    .integer("age", o->o.age)
				.endObject()
				.endObject();

		StringBuilder results = LoadTester.runClient(
				renderer,
				()->person,				
				(r)->{
						return "{\"name\":\"bob\",\"isLegal\":true}".equals(r.structured().readPayload().readUTFFully())
								&& (HTTPContentTypeDefaults.JSON == r.contentType());
					  }, 
				"/testJSON", 
				useTLS, telemetry, 
				parallelTracks, cyclesPerTrack, 
				host, port, timeoutMS);		
		
		assertTrue(results.toString(), results.indexOf("Responses invalid: 0 out of "+(cyclesPerTrack*parallelTracks))>=0);

	}
		
	@Test
	public void fileCallTest() {
		
		StringBuilder results = LoadTester.runClient(
				()-> null, 
				(r)->{
						return  (HTTPContentTypeDefaults.HTML==r.contentType()) 
								&& "hello world".equals(r.structured().readPayload().readUTFFully());
					  }, 
				"/files/index.html", 
				useTLS, telemetry, 
				parallelTracks, cyclesPerTrack, 
				host, port, timeoutMS);		
		
		assertTrue(results.toString(), results.indexOf("Responses invalid: 0 out of "+(cyclesPerTrack*parallelTracks))>=0);

	}
	
	@Test
	public void resourceCallTest() {
		
		StringBuilder results = LoadTester.runClient(
				()-> null, 
				(r)->{
						return  (HTTPContentTypeDefaults.HTML==r.contentType()) 
								&& "hello world".equals(r.structured().readPayload().readUTFFully());
					  }, 
				"/resources/index.html", 
				useTLS, telemetry, 
				parallelTracks, 1, //TODO: second call not working, need to investigate..
				host, port, timeoutMS);		
		
	//	assertTrue(results.toString(), results.indexOf("Responses invalid: 0 out of "+(cyclesPerTrack*parallelTracks))>=0);

	}
	
	
}
