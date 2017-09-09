# What you will need before you start:
-[Java 8](https://docs.oracle.com/javase/8/docs/technotes/guides/install/install_overview.html) 

-[Maven](https://maven.apache.org/install.html), which downloads and manages the libraries and APIs needed to get the Grove device working.

-[Git](https://git-scm.com/), which clones a template Maven project with the necessary dependencies already set up.

# Starting your Maven project: 
[Starting a mvn project](https://github.com/oci-pronghorn/FogLighter/blob/master/README.md)

# Example project:

Demo code:

```java
package com.ociweb.oe.foglight.api;


import com.ociweb.iot.maker.FogApp;
import com.ociweb.iot.maker.FogRuntime;
import com.ociweb.iot.maker.Hardware;
import com.ociweb.pronghorn.network.config.HTTPHeaderDefaults;

public class HTTPServer implements FogApp
{
	byte[] cookieHeader = HTTPHeaderDefaults.COOKIE.rootBytes();
	
	int emptyResponseRouteId;
	int smallResponseRouteId;
	int largeResponseRouteId;
	int fileServerId;
	
	
	byte[] myArgName = "myarg".getBytes();
	
    @Override
    public void declareConnections(Hardware c) {
        
		c.enableServer(false, 8088);    	
		emptyResponseRouteId = c.registerRoute("/testpageA?arg=#{myarg}", cookieHeader);
		smallResponseRouteId = c.registerRoute("/testpageB");
		largeResponseRouteId = c.registerRoute("/testpageC", cookieHeader);
		fileServerId         = c.registerRoute("/file${path}");
		c.enableTelemetry();
		
    }


    @Override
    public void declareBehavior(FogRuntime runtime) {
        runtime.addRestListener(new RestBehaviorEmptyResponse(runtime, myArgName))
                 .includeRoutes(emptyResponseRouteId);
        
        runtime.addRestListener(new RestBehaviorSmallResponse(runtime))
        		.includeRoutes(smallResponseRouteId);
        
        runtime.addRestListener(new RestBehaviorLargeResponse(runtime))
        		 .includeRoutes(largeResponseRouteId);
        
        //NOTE .includeAllRoutes() can be used to write a behavior taking all routes
        
        //NOTE when using the above no routes need to be registered and if they are
        //     all other routes will return a 404

    }
   
}
```

Behavior class:

```java
package com.ociweb.oe.foglight.api;

import com.ociweb.gl.api.HTTPRequestReader;
import com.ociweb.gl.api.Headable;
import com.ociweb.gl.api.Payloadable;
import com.ociweb.gl.api.RestListener;
import com.ociweb.iot.maker.FogCommandChannel;
import com.ociweb.iot.maker.FogRuntime;
import com.ociweb.pronghorn.network.config.HTTPHeaderDefaults;
import com.ociweb.pronghorn.pipe.BlobReader;

public class RestBehaviorEmptyResponse implements RestListener {

	final byte[] cookieHeader = HTTPHeaderDefaults.COOKIE.rootBytes();
	final byte[] fieldName;
	private final FogCommandChannel cmd;
	
	public RestBehaviorEmptyResponse(FogRuntime runtime, byte[] myArgName) {
		this.fieldName = myArgName;		
		this.cmd = runtime.newCommandChannel(NET_RESPONDER);
	}
	
	Payloadable reader = new Payloadable() {
		
		@Override
		public void read(BlobReader reader) {
			
			System.out.println("POST: "+reader.readUTFOfLength(reader.available()));
			
		}			
	};

	private Headable headReader = new Headable() {

		@Override
		public void read(BlobReader reader) { 
			
			System.out.println("COOKIE: "+reader.readUTFOfLength(reader.available()));
						
		}
		
	};


	@Override
	public boolean restRequest(HTTPRequestReader request) {
		
	    int argInt = request.getInt(fieldName);
	    System.out.println("Arg Int: "+argInt);
		
		request.openHeaderData(cookieHeader, headReader);
		
		if (request.isVerbPost()) {
			request.openPayloadData(reader );
		}
		
		//no body just a 200 ok response.
		return cmd.publishHTTPResponse(request, 200);

	}

}
```


```java
package com.ociweb.oe.foglight.api;

import com.ociweb.gl.api.HTTPFieldReader;
import com.ociweb.gl.api.HTTPRequestReader;
import com.ociweb.gl.api.NetResponseWriter;
import com.ociweb.gl.api.Writable;
import com.ociweb.gl.api.Payloadable;
import com.ociweb.gl.api.RestListener;
import com.ociweb.iot.maker.FogCommandChannel;
import com.ociweb.iot.maker.FogRuntime;
import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;
import com.ociweb.pronghorn.pipe.BlobReader;
import com.ociweb.pronghorn.pipe.BlobWriter;

public class RestBehaviorLargeResponse implements RestListener {

	private final FogCommandChannel cmd;
	private int partNeeded = 0;
	
	public RestBehaviorLargeResponse(FogRuntime runtime) {	
		this.cmd = runtime.newCommandChannel(NET_RESPONDER);
	}
	
	Payloadable reader = new Payloadable() {
		
		@Override
		public void read(BlobReader reader) {
			
			System.out.println("POST: "+reader.readUTFOfLength(reader.available()));
			
		}			
	};


	Writable writableA = new Writable() {
		
		@Override
		public void write(BlobWriter writer) {
			writer.writeUTF8Text("beginning of text file\n");//23 in length
		}
		
	};
	
	Writable writableB = new Writable() {
		
		@Override
		public void write(BlobWriter writer) {
			writer.writeUTF8Text("ending of text file\n");//20 in length
		}
		
	};
	
	@Override
	public boolean restRequest(HTTPRequestReader request) {
		
		if (request.isVerbPost()) {
			request.openPayloadData(reader);
		}
		
		if (0 == partNeeded) {
			boolean okA = cmd.publishHTTPResponse(request, 200, 
									request.getRequestContext(),
					                HTTPContentTypeDefaults.TXT,
					                writableA);
			if (!okA) {
				return false;
			} 
		}
				
		//////
		//NB: this block is here for demo reasons however one could
		//    publish a topic back to this behavior to complete the
		//    continuaton at a future time
		//////
	
		boolean okB = cmd.publishHTTPResponseContinuation(request,
						 		request.getRequestContext() | HTTPFieldReader.END_OF_RESPONSE,
						 		writableB);
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
package com.ociweb.oe.foglight.api;

import com.ociweb.gl.api.HTTPFieldReader;
import com.ociweb.gl.api.HTTPRequestReader;
import com.ociweb.gl.api.NetResponseWriter;
import com.ociweb.gl.api.Writable;
import com.ociweb.gl.api.Payloadable;
import com.ociweb.gl.api.RestListener;
import com.ociweb.iot.maker.FogCommandChannel;
import com.ociweb.iot.maker.FogRuntime;
import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;
import com.ociweb.pronghorn.pipe.BlobReader;
import com.ociweb.pronghorn.pipe.BlobWriter;

public class RestBehaviorSmallResponse implements RestListener {

	private final FogCommandChannel cmd;
	
	public RestBehaviorSmallResponse(FogRuntime runtime) {	
		this.cmd = runtime.newCommandChannel(NET_RESPONDER);
	}
	
	Payloadable reader = new Payloadable() {
		
		@Override
		public void read(BlobReader reader) {
			
			System.out.println("POST: "+reader.readUTFOfLength(reader.available()));
			
		}			
	};


	Writable writableA = new Writable() {
		
		@Override
		public void write(BlobWriter writer) {
			writer.writeUTF8Text("beginning of text file\n");
		}
		
	};
	
	Writable writableB = new Writable() {
		
		@Override
		public void write(BlobWriter writer) {
			writer.writeUTF8Text("this is some text\n");
		}
		
	};
	
	@Override
	public boolean restRequest(HTTPRequestReader request) {
		
		if (request.isVerbPost()) {
			request.openPayloadData(reader );
		}

		//if this can not be published then we will get the request again later to be reattempted.
		return cmd.publishHTTPResponse(request, 200, 
								request.getRequestContext() | HTTPFieldReader.END_OF_RESPONSE,
				                HTTPContentTypeDefaults.TXT,
				                writableA);

	}

}
```

This class is a simple demonstration of HTTPServer. HTTP Server will listen for an HTTP Client to send it a request. It will try to process the request, however, if it is unable to process it at that time, it will be reattempted later
