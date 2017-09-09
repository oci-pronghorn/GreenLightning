# What you will need before you start:
-[Java 8](https://docs.oracle.com/javase/8/docs/technotes/guides/install/install_overview.html) 

-[Maven](https://maven.apache.org/install.html), which downloads and manages the libraries and APIs needed to get the Grove device working.

-[Git](https://git-scm.com/), which clones a template Maven project with the necessary dependencies already set up.

# Starting your Maven project: 
[Starting a mvn project](https://github.com/oci-pronghorn/FogLighter/blob/master/README.md)

# Example project:

Demo code:

```java
package com.ociweb.oe.floglight.api;


import com.ociweb.iot.maker.FogApp;
import com.ociweb.iot.maker.FogRuntime;
import com.ociweb.iot.maker.Hardware;

public class HTTPClient implements FogApp
{

    @Override
    public void declareConnections(Hardware c) {   
    	c.useNetClient();
    	c.enableTelemetry();
    }

    @Override
    public void declareBehavior(FogRuntime runtime) {       
    	
    	HTTPGetBehaviorSingle temp = new HTTPGetBehaviorSingle(runtime);
		runtime.addStartupListener(temp);
			   	
    	
    	int responseId = runtime.addResponseListener(new HTTPResponse()).getId();    	
    	runtime.addStartupListener(new HTTPGetBehaviorChained(runtime, responseId));
    	
    	
    	
    }
          
}
```

Behavior class:

```java
package com.ociweb.oe.floglight.api;

import com.ociweb.gl.api.StartupListener;
import com.ociweb.iot.maker.FogCommandChannel;
import com.ociweb.iot.maker.FogRuntime;

public class HTTPGetBehaviorChained implements StartupListener {
	
	private FogCommandChannel cmd;
	private int responseId;

	public HTTPGetBehaviorChained(FogRuntime runtime, int responseId) {
		this.cmd = runtime.newCommandChannel(NET_REQUESTER);
		this.responseId = responseId;
	}

	@Override
	public void startup() {
		
		cmd.httpGet("www.objectcomputing.com", "/", responseId);
		
	}

}
```


```java
package com.ociweb.oe.floglight.api;

import com.ociweb.gl.api.HTTPResponseListener;
import com.ociweb.gl.api.HTTPResponseReader;
import com.ociweb.gl.api.Payloadable;
import com.ociweb.gl.api.StartupListener;
import com.ociweb.iot.maker.FogCommandChannel;
import com.ociweb.iot.maker.FogRuntime;
import com.ociweb.pronghorn.network.config.HTTPContentType;
import com.ociweb.pronghorn.pipe.BlobReader;

public class HTTPGetBehaviorSingle implements StartupListener, HTTPResponseListener {

	
	private final FogCommandChannel cmd;

	public HTTPGetBehaviorSingle(FogRuntime runtime) {
		cmd = runtime.newCommandChannel(NET_REQUESTER);
	}

	@Override
	public void startup() {
		cmd.httpGet("www.objectcomputing.com", "/");
	}

	@Override
	public boolean responseHTTP(HTTPResponseReader reader) {
		
		System.out.println(" status:"+reader.statusCode());
		System.out.println("   type:"+reader.contentType());
		
		Payloadable payload = new Payloadable() {
			@Override
			public void read(BlobReader reader) {
				System.out.println(reader.readUTFOfLength(reader.available()));
			}
		};

		reader.openPayloadData( payload );
		
		return true;
	}

}
```


```java
package com.ociweb.oe.floglight.api;

import com.ociweb.gl.api.HTTPResponseListener;
import com.ociweb.gl.api.HTTPResponseReader;
import com.ociweb.gl.api.Payloadable;
import com.ociweb.pronghorn.network.config.HTTPContentType;
import com.ociweb.pronghorn.pipe.BlobReader;

public class HTTPResponse implements HTTPResponseListener {

	@Override
	public boolean responseHTTP(HTTPResponseReader reader) {
		
		System.out.println(" status:"+reader.statusCode());
		System.out.println("   type:"+reader.contentType());

		Payloadable payload = new Payloadable() {
			@Override
			public void read(BlobReader reader) {
				System.out.println(reader.readUTFOfLength(reader.available()));
			}
		};
		boolean hadAbody = reader.openPayloadData(payload );

		
		return true;
	}

}
```


This class is a simple demonstration of an HTTP Client. HTTP Client will send a request out to an HTTP Server. In this case, the client is sending a request to go to "www.objectcomputing.com".