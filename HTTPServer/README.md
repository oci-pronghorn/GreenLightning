# What you will need before you start:
-[Java 8](https://docs.oracle.com/javase/8/docs/technotes/guides/install/install_overview.html) 

-[Maven](https://maven.apache.org/install.html), which downloads and manages the libraries and APIs needed to get the Grove device working.

-[Git](https://git-scm.com/), which clones a template Maven project with the necessary dependencies already set up.

# Starting your Maven project: 
[Starting a mvn project](https://github.com/oci-pronghorn/FogLighter/blob/master/README.md)

# Example project:

Demo code:

```java
package com.ociweb.oe.greenlightning.api;


import com.ociweb.gl.api.Builder;
import com.ociweb.gl.api.GreenApp;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.pronghorn.network.config.HTTPHeaderDefaults;
import com.ociweb.pronghorn.util.AppendableProxy;
import com.ociweb.pronghorn.util.Appendables;

public class HTTPServer implements GreenApp
{
	private byte[] cookieHeader = HTTPHeaderDefaults.COOKIE.rootBytes();
	
	private int emptyResponseRouteId;
	private int smallResponseRouteId;
	private int largeResponseRouteId;
	private int splitResponseRouteId;
	private int shutdownRouteId;
		
	private AppendableProxy console;
	private final String host;
	private final int port;
	
	public HTTPServer(String host, int port, Appendable console) {
		this.host = host;
		this.console = Appendables.proxy(console);
		this.port = port;
	}
	
	public HTTPServer(int port, Appendable console) {
		this.host = null;
		this.console = Appendables.proxy(console);
		this.port = port;
	}
	
    @Override
    public void declareConfiguration(Builder c) {
        
		c.useHTTP1xServer(port).setHost(host);
		
		emptyResponseRouteId = c.registerRoute("/testpageA?arg=#{myarg}", cookieHeader);
		smallResponseRouteId = c.registerRoute("/testpageB");
		largeResponseRouteId = c.registerRoute("/testpageC", cookieHeader);
		splitResponseRouteId = c.registerRoute("/testpageD");
		
		//only do in test mode... 
		//in production it is a bad idea to let clients turn off server.
		shutdownRouteId = c.registerRoute("/shutdown?key=${key}");
				
		c.enableTelemetry();
		
    }


    @Override
    public void declareBehavior(GreenRuntime runtime) {
    	
        runtime.addRestListener(new RestBehaviorEmptyResponse(runtime, "myarg", console))
                 .includeRoutes(emptyResponseRouteId);
        
        runtime.addRestListener(new RestBehaviorSmallResponse(runtime, console))
        		.includeRoutes(smallResponseRouteId);
        
        runtime.addRestListener(new RestBehaviorLargeResponse(runtime, console))
        		 .includeRoutes(largeResponseRouteId);
        
        runtime.addRestListener(new RestBehaviorHandoff(runtime, "responder"))
        		 .includeRoutes(splitResponseRouteId);
        
        runtime.addPubSubListener(new RestBehaviorHandoffResponder(runtime, console))
		         .addSubscription("responder");
        


        
        //splitResponseRouteId
        
        runtime.addRestListener(new ShutdownRestListener(runtime))
                  .includeRoutes(shutdownRouteId);
        
        //NOTE .includeAllRoutes() can be used to write a behavior taking all routes

    }
   
}
```

Behavior class:

```java
package com.ociweb.oe.greenlightning.api;

import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPRequestReader;
import com.ociweb.gl.api.RestListener;
import com.ociweb.pronghorn.network.config.HTTPHeaderDefaults;
import com.ociweb.pronghorn.util.AppendableProxy;
import com.ociweb.pronghorn.util.Appendables;

public class RestBehaviorEmptyResponse implements RestListener {

	private final int cookieHeader = HTTPHeaderDefaults.COOKIE.ordinal();
	private final byte[] fieldName;
	private final GreenCommandChannel cmd;
	private final AppendableProxy console;
	
	public RestBehaviorEmptyResponse(GreenRuntime runtime, String myArgName, AppendableProxy console) {
		this.fieldName = myArgName.getBytes();		
		this.cmd = runtime.newCommandChannel(NET_RESPONDER);
		this.console = console;
	}

	@Override
	public boolean restRequest(HTTPRequestReader request) {
		
	    int argInt = request.getInt(fieldName);
	    Appendables.appendValue(console, "Arg Int: ", argInt, "\n");
	    		
		request.openHeaderData(cookieHeader, (id,reader)-> {
			
			console.append("COOKIE: ");
			reader.readUTF(console).append('\n');
					
		});
		
		if (request.isVerbPost()) {
			request.openPayloadData((reader)->{
				
				console.append("POST: ");
				reader.readUTFOfLength(reader.available(), console);
				console.append('\n');
									
			});
		}
		
		//no body just a 200 ok response.
		return cmd.publishHTTPResponse(request, 200);

	}

}
```


```java
package com.ociweb.oe.greenlightning.api;

import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPFieldReader;
import com.ociweb.gl.api.HTTPRequestReader;
import com.ociweb.gl.api.RestListener;
import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;
import com.ociweb.pronghorn.network.config.HTTPHeaderDefaults;
import com.ociweb.pronghorn.util.AppendableProxy;

public class RestBehaviorLargeResponse implements RestListener {

	private final int cookieHeader = HTTPHeaderDefaults.COOKIE.ordinal();
	private final GreenCommandChannel cmd;
	private int partNeeded = 0;
	private final AppendableProxy console;
	
	public RestBehaviorLargeResponse(GreenRuntime runtime, AppendableProxy console) {	
		this.cmd = runtime.newCommandChannel(NET_RESPONDER);
		this.console = console;
	}
	
	@Override
	public boolean restRequest(HTTPRequestReader request) {
		
		if (request.isVerbPost()) {
			request.openPayloadData((reader)->{
				
				console.append("POST: ");
				//TODO: why is this payload pointing to the cookie??
				//reader.readUTF(console);
				reader.readUTFOfLength(reader.available(),console);
				console.append('\n');
				
			});
		}
		
		request.openHeaderData(cookieHeader, (id,reader)-> {
			
			console.append("COOKIE: ");
			reader.readUTF(console).append('\n');
					
		});
		
		if (0 == partNeeded) {
			boolean okA = cmd.publishHTTPResponse(request, 200, 
									true,
					                HTTPContentTypeDefaults.TXT,
					                (writer)->{
					                	writer.writeUTF8Text("beginning of text file\n");
					                });
			if (!okA) {
				return false;
			} 
		}
				
		//////
		//NB: this block is here for demo reasons however one could
		//    publish a topic back to this behavior to complete the
		//    continuation at a future time
		//////
	
		boolean okB = cmd.publishHTTPResponseContinuation(request,
						 		false,
						 		(writer)-> {
						 			writer.writeUTF8Text("ending of text file\n");
						 		});
		if (okB) {
			partNeeded = 0;
			return true;
		} else {
			partNeeded = 1;
			return false;
		}
	}

}
```


```java
package com.ociweb.oe.greenlightning.api;

import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPFieldReader;
import com.ociweb.gl.api.HTTPRequestReader;
import com.ociweb.gl.api.RestListener;
import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;
import com.ociweb.pronghorn.util.AppendableProxy;

public class RestBehaviorSmallResponse implements RestListener {

	private final GreenCommandChannel cmd;
	private final AppendableProxy console;
	
	public RestBehaviorSmallResponse(GreenRuntime runtime, AppendableProxy console) {	
		this.cmd = runtime.newCommandChannel(NET_RESPONDER);
		this.console = console;
	}
	
	@Override
	public boolean restRequest(HTTPRequestReader request) {
		
		if (request.isVerbPost()) {
			request.openPayloadData((reader)->{
				
				console.append("POST: ");
				reader.readUTFOfLength(reader.available(),console);
								
			});
		}

		//if this can not be published then we will get the request again later to be reattempted.
		return cmd.publishHTTPResponse(request, 200, 
								false,
				                HTTPContentTypeDefaults.TXT,
				                (writer)-> {
				                	writer.writeUTF8Text("beginning of text file\n");
				                });

	}

}
```

This class is a simple demonstration of HTTPServer. HTTP Server will listen for an HTTP Client to send it a request. It will try to process the request, however, if it is unable to process it at that time, it will be reattempted later
