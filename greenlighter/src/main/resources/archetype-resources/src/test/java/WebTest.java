package ${package};
/**
 * ************************************************************************
 * For greenlightning support, training or feature reqeusts please contact:
 *   info@objectcomputing.com   (314) 579-0066
 * ************************************************************************
 */
import static org.junit.Assert.assertTrue;

import java.util.Random;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.MsgRuntime;
import com.ociweb.gl.test.LoadTester;
import com.ociweb.json.encode.JSONRenderer;
import com.ociweb.pronghorn.util.AppendableBuilder;
import com.ociweb.pronghorn.util.Appendables;
import com.test.WebTest.Product;

public class WebTest {
	
	static GreenRuntime runtime;
	
	static int port = 8050;
	static int telemetryPort = 8097;
	static String host = "127.0.0.1";	
	static int timeoutMS = 60_000;	
	static boolean telemetry = false;
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
		
//	public class Product {
//		public int id;
//		public int quantity;
//		public boolean disabled;
//		public String name;
//		
//		public Product(int i) {
//			
//			Random r = new Random(i);
//			id = i;
//			quantity = Math.abs(r.nextInt(1000));
//			disabled = r.nextBoolean();
//			byte[] temp = new byte[10];
//			r.nextBytes(temp);
//			name = Appendables.appendBase64Encoded(new StringBuilder(), temp, 0, temp.length, Integer.MAX_VALUE).toString();
//
//		}
//	}
//
//	
//	private JSONRenderer<Product> renderer = new JSONRenderer<Product>()
//			.startObject()
//			.integer(   "id", o->o.id)
//			.integer(   "quantity", o->o.quantity)
//			.string(    "name", (o,t)->t.append(o.name))
//			.bool(      "disabled", o->o.disabled)
//			.endObject();
//	
//	@Test
//	public void uploadProductsTest() {
//				
//				int inFlightBits = 0;
//				int tracks = 8;
//				int callsPerTrack = 10000/tracks; 
//		
//				StringBuilder uploadConsoleCapture = new StringBuilder();
//		    	LoadTester.runClient(
//						(i,w) -> renderer.render(w, new Product((int)i)) ,
//						(i,r) -> r.statusCode()==200 , 
//						"/update", 
//						useTLS, false, 
//						tracks, callsPerTrack, 
//						host, port, timeoutMS, inFlightBits,
//						MsgRuntime.getGraphManager(runtime),						
//						Appendables.join(uploadConsoleCapture,System.out));	
//				
//				assertTrue(uploadConsoleCapture.toString(), uploadConsoleCapture.indexOf("Responses invalid: 0 out of "+(tracks*callsPerTrack))>=0);
//				
//				//////////////////////////////////////
//				//now test that we get the values back
//				//////////////////////////////////////
//				
//				tracks = 1; //this test depends on having sequential tests
//				callsPerTrack = 10;
//				
//				
//				StringBuilder captured = new StringBuilder();
//				
//				LoadTester.runClient(
//					 null, 
//					(i,r) -> {
//							AppendableBuilder target = new AppendableBuilder(1000);								
//							renderer.render(target, new Product((int)i));
//							
//							return  (200 == r.statusCode()) &&
//									(target.toString().equals(r.structured().readPayload().readUTFFully()))	;
//							
//						  }, 
//					(i) -> "/query?id="+i,
//					useTLS, false, 
//					tracks, callsPerTrack, 
//					host, port, timeoutMS,
//					captured);		
//		
//				 assertTrue(captured.toString(), captured.indexOf("Responses invalid: 0 out of "+(tracks*callsPerTrack))>=0);
//
//	}
	
	
}
