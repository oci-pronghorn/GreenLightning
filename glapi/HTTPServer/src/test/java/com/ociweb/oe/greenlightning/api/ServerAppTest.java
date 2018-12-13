package com.ociweb.oe.greenlightning.api;

import static org.junit.Assert.assertTrue;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.Writable;
import com.ociweb.gl.test.LoadTester;
import com.ociweb.json.encode.JSONRenderer;
import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;
import com.ociweb.pronghorn.pipe.ChannelWriter;
import com.ociweb.pronghorn.util.Appendables;

public class ServerAppTest {

	public class Person {

		public final String name;
		public final int age;
		
		public Person(String name, int age) {
			this.name=name;
			this.age=age;
		}

	}

	static GreenRuntime runtime;
	static StringBuilder console;
	
	static int port = (int) (3000 + (System.nanoTime()%12000));
	static int telemetryPort = -1;//8097;
	static String host = "127.0.0.1";
	
	int timeoutMS = 60_000;	
	boolean telemetry = false;
	int cyclesPerTrack = 100;

	static boolean useTLS = true;
	int parallelTracks = 2; //NOTE: this number must be lower than the server connections when using TLS because rounds of handshake may cause hang.
	
	@BeforeClass
	public static void startServer() {
		
		//speed up load testers
		LoadTester.cycleRate = 4_0000;
		
		
		console = new StringBuilder();
		runtime = GreenRuntime.run(new HTTPServer(host,port,console,telemetryPort, useTLS));
		
	}
		
	@AfterClass
	public static void stopServer() {
		runtime.shutdownRuntime();	
		runtime = null;
	}
	
	@Test
	public void jsonCallTest() {
		
		int cycles = 3;
		int tracks = 4;
		
		 Writable testData = new Writable() {			 
				@Override
				public void write(ChannelWriter writer) {
					writer.append("{\"person\":{\"name\":\"bob\",\"age\":42}}");
				}						
			};
		
		StringBuilder results = new StringBuilder(); 
		LoadTester.runClient(
				(i,w)->testData.write(w), 
				(i,r)->{
						String readUTFFully = r.structured().readPayload().readUTFFully();
						boolean isMatch = "{\"name\":\"bob\",\"isLegal\":true}".equals(readUTFFully);
						if (!isMatch) {
							System.out.println("bad response: "+readUTFFully);
						}
						return isMatch && (HTTPContentTypeDefaults.JSON == r.contentType());
					  }, 
				"/testJSON", 
				useTLS, telemetry, 
				tracks, cycles,
				host, port, timeoutMS, results);		
		
		assertTrue(results.toString(), results.indexOf("Responses invalid: 0 out of "+(cycles*tracks))>=0);

	}
	
	@Test
	public void jsonCall2Test() {
		
		int cycles = 3;
		int tracks = 4;
		
		Person person = new Person("bob",42);
		JSONRenderer<Person> renderer = new JSONRenderer<Person>()
				.startObject()
				.startObject("person")
				    .string("name", (o,t)->t.append(o.name))
				    .integer("age", o->o.age)
				.endObject()
				.endObject();

		StringBuilder results = new StringBuilder();
		LoadTester.runClient(				
				(i,w)-> renderer.render(w, person),				
				(i,r)->{
						return "{\"name\":\"bob\",\"isLegal\":true}".equals(r.structured().readPayload().readUTFFully())
								&& (HTTPContentTypeDefaults.JSON == r.contentType());
					  }, 
				"/testJSON", 
				useTLS, telemetry, 
				tracks, cycles,
				host, port, timeoutMS, results);		
		
		assertTrue(results.toString(), results.indexOf("Responses invalid: 0 out of "+(cycles*tracks))>=0);
	}
		
	@Test
	public void fileCallTest() {
		
		int cycles = 3;
		int tracks = 4;
		
		StringBuilder results = new StringBuilder(); 
		LoadTester.runClient(
				null, 
				(i,r)->{
						String payload = r.structured().readPayload().readUTFFully();
						boolean matches = 200==r.statusCode()
							&& (HTTPContentTypeDefaults.HTML==r.contentType()) 
							&& "hello world".equals(payload);
						if (!matches) {
							
							System.out.println("response code: "+r.statusCode());
							System.out.println("content type: "+r.contentType());
							System.out.println("payload: "+payload);
							
						}						
						return  matches;
						
					  }, 
				"/files/index.html", 
				useTLS, telemetry, 
				tracks, cycles,
				host, port, timeoutMS, results);	
		
		assertTrue(results.toString(), results.indexOf("Responses invalid: 0 out of "+(cycles*tracks))>=0);
	}
	
	@Test
	public void resourceCallTest() {
		
		
		int cycles = 1; //TODO: why is this not wokring with larger values?
		int tracks = 4;
		
		StringBuilder results = new StringBuilder(); 
		LoadTester.runClient(
				null, 
				(i,r)->{
					   // System.out.println("Check now"); //Why is this so slow!!
						return  (HTTPContentTypeDefaults.HTML==r.contentType()) 
								&& "hello world".equals(r.structured().readPayload().readUTFFully());
					  }, 
				"/resources/index.html", 
				useTLS, telemetry, 
				tracks, cycles,
				host, port, timeoutMS, Appendables.join(results,System.out));		
		
		assertTrue(results.toString(), results.indexOf("Responses invalid: 0 out of "+(cycles*tracks))>=0);

	}
	
	@Test
	public void pageBTest() {
		
		int cycles = 3;
		int tracks = 4;
		
		StringBuilder results = new StringBuilder(); 
		LoadTester.runClient(
				null, 
				(i,r)->{					
						return  (HTTPContentTypeDefaults.PLAIN==r.contentType()) 
								&& "beginning of text file\n".equals(r.structured().readPayload().readUTFFully());
					  }, 
				"/testPageB", 
				useTLS, telemetry, 
				tracks, cycles, 
				host, port, timeoutMS, results);		
		
		assertTrue(results.toString(), results.indexOf("Responses invalid: 0 out of "+(cycles*tracks))>=0);

	}

	@Test
	public void pageATest() {
		
		int cycles = 3;
		int tracks = 4;
		
		console.setLength(0);
		
		StringBuilder results = new StringBuilder();
		LoadTester.runClient(
				null, 
				(i,r)-> 0 == r.structured().readPayload().available(), 
				"/testpageA?arg=42", 
				useTLS, telemetry, 
				tracks, cycles, 
				host, port, timeoutMS, results);		

		
		//Cookies turned off in tester
		//assertTrue(console.toString(), console.indexOf("Arg Int: 42\nCOOKIE: ")>=0); //test adds a cookie by default..

		assertTrue(results.toString(), results.indexOf("Responses invalid: 0 out of "+(cycles*tracks))>=0);

		
	}
	
	@Test
	public void pageADefaultTest() {
		
		
		
		int cycles = 3;
		int tracks = 4;
		console.setLength(0);
		
		StringBuilder results = new StringBuilder();
		LoadTester.runClient(
				null, 
				(i,r)-> 0 == r.structured().readPayload().available(), 
				"/testpageA?f=g", 
				useTLS, telemetry, 
				tracks, cycles, 
				host, port, timeoutMS, results);		

		//Cookies turned off in tester
		//assertTrue(console.toString(), console.indexOf("Arg Int: 111\nCOOKIE: ")>=0); //test adds a cookie by default..

		assertTrue(results.toString(), results.indexOf("Responses invalid: 0 out of "+(cycles*tracks))>=0);

		
	}
	
	@Test
	public void pageCTest() {
		
		int cycles = 3;
		int tracks = 4;
		
		console.setLength(0);
		
		StringBuilder results = new StringBuilder();
		LoadTester.runClient(
				null, 
				(i,r)-> 0 == r.structured().readPayload().available(), 
				"/testPageC", 
				useTLS, telemetry, 
				tracks, cycles, 
				host, port, timeoutMS, results);		
		
		////Cookies turned off in tester
		//assertTrue(console.toString(), console.indexOf("COOKIE: ")>=0); //test adds a cookie by default..

		assertTrue(results.toString(), results.indexOf("Responses invalid: 0 out of "+(cycles*tracks))>=0);	
	}
	
	
	@Test
	public void pageDTest() {
		
		
		int cycles = 3;
		int tracks = 4;
		
		console.setLength(0);
		
		StringBuilder results = new StringBuilder();
		LoadTester.runClient(
				(i,w)-> w.append("payload"), 
				(i,r)-> {
						return "sent by responder".equals(r.structured().readPayload().readUTFFully());
					},
				"/testpageD", 
				useTLS, telemetry, 
				tracks, cycles, 
				host, port, timeoutMS, results);		
		
		assertTrue(results.toString(), results.indexOf("Responses invalid: 0 out of "+(cycles*tracks))>=0);	
	}
}
