package ${package};

import static org.junit.Assert.assertTrue;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.Writable;
import com.ociweb.gl.test.LoadTester;
import com.ociweb.json.encode.JSONRenderer;
import com.ociweb.oe.greenlightning.api.HTTPServer;
import com.ociweb.oe.greenlightning.api.ExampleAppTest.Person;
import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;
import com.ociweb.pronghorn.pipe.ChannelWriter;

public class WebTest {
	
	GreenRuntime runtime;
	
	static int port = 8050;
	static int telemetryPort = 8097;
	static String host = "127.0.0.1";	
	static int timeoutMS = 60_000;	
	static bboolean telemetry = false;
	static int cyclesPerTrack = 100;
	static boolean useTLS = true;
	static int parallelTracks = 2;
	
	
	@BeforeClass
	public static void startServer() {
		
		runtime = GreenRuntime.run(new ${mainClass}());
		
	}
		
	@AfterClass
	public static void stopServer() {
		runtime.shutdownRuntime();	
		runtime = null;
	}
	
//	@Test
//	public void getExampleTest() {
//		
//		StringBuilder results = LoadTester.runClient(
//				()-> null, 
//				(r)->{					
//						return  (HTTPContentTypeDefaults.PLAIN==r.contentType()) 
//								&& "beginning of text file\n".equals(r.structured().readPayload().readUTFFully());
//					  }, 
//				"/testPageB", 
//				useTLS, telemetry, 
//				parallelTracks, cyclesPerTrack, 
//				host, port, timeoutMS);		
//		
//		assertTrue(results.toString(), results.indexOf("Responses invalid: 0 out of "+(cyclesPerTrack*parallelTracks))>=0);
//
//	}
	
//	@Test
//	public void postExampleTest() {
//		
//		 Writable testData = new Writable() {			 
//				@Override
//				public void write(ChannelWriter writer) {
//					writer.append("{\"person\":{\"name\":\"bob\",\"age\":42}}");
//				}						
//			};
//		
//		StringBuilder results = LoadTester.runClient(
//				()->testData, 
//				(r)->{
//						String readUTFFully = r.structured().readPayload().readUTFFully();
//						boolean isMatch = "{\"name\":\"bob\",\"isLegal\":true}".equals(readUTFFully);
//						if (!isMatch) {
//							System.out.println("bad response: "+readUTFFully);
//						}
//						return isMatch && (HTTPContentTypeDefaults.JSON == r.contentType());
//					  }, 
//				"/testJSON", 
//				useTLS, telemetry, 
//				parallelTracks, cyclesPerTrack, 
//				host, port, timeoutMS);		
//		
//		assertTrue(results.toString(), results.indexOf("Responses invalid: 0 out of "+(cyclesPerTrack*parallelTracks))>=0);
//
//	}
	
	
//	@Test
//	public void jsonExampleTest() {
//		
//		Person person = new Person("bob",42);
//		JSONRenderer<Person> renderer = new JSONRenderer<Person>()
//				.beginObject()
//				.beginObject("person")
//				    .string("name", (o,t)->t.append(o.name))
//				    .integer("age", o->o.age)
//				.endObject()
//				.endObject();
//
//		StringBuilder results = LoadTester.runClient(
//				renderer,
//				()->person,				
//				(r)->{
//						return "{\"name\":\"bob\",\"isLegal\":true}".equals(r.structured().readPayload().readUTFFully())
//								&& (HTTPContentTypeDefaults.JSON == r.contentType());
//					  }, 
//				"/testJSON", 
//				useTLS, telemetry, 
//				parallelTracks, cyclesPerTrack, 
//				host, port, timeoutMS);		
//		
//		assertTrue(results.toString(), results.indexOf("Responses invalid: 0 out of "+(cyclesPerTrack*parallelTracks))>=0);
//
//	}
	
	
}
