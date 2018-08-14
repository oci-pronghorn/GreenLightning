# What you will need before you start:
-[Java 8](https://docs.oracle.com/javase/8/docs/technotes/guides/install/install_overview.html) 

-[Maven](https://maven.apache.org/install.html), which downloads and manages the libraries and APIs needed to get the Grove device working.

-[Git](https://git-scm.com/), which clones a template Maven project with the necessary dependencies already set up.

# Starting your Maven project: 
[Starting a mvn project](https://github.com/oci-pronghorn/FogLighter/blob/master/README.md)

# Example project:

The following sketch will demonstrate a basic demo for using a ```Shutdown()```.

Demo code:


```java
package com.ociweb.oe.greenlightning.api;

import com.ociweb.gl.api.Builder;
import com.ociweb.gl.api.GreenApp;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPServerConfig;
import com.ociweb.pronghorn.network.NetGraphBuilder;


public class Shutdown implements GreenApp
{	
	private final String host;
	public Shutdown(String host) {
		this.host = host;
	}
	
	public Shutdown() {
		this.host = null;
	}
	
    @Override    
    public void declareConfiguration(Builder c) {
    	
    	HTTPServerConfig conf = c.useHTTP1xServer(8443)
    			.setHost(null==host?NetGraphBuilder.bindHost():host)
    			.setDefaultPath("");
    	    	
    	c.defineRoute("/shutdown?key=${key}");
    }
  
    @Override
    public void declareBehavior(final GreenRuntime runtime) {
    	runtime.registerListener(new ShutdownBehavior(runtime)).includeAllRoutes();	
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
import com.ociweb.gl.api.ShutdownListener;

public class ShutdownBehavior implements ShutdownListener, RestListener{

	private final GreenCommandChannel channel;
	private final byte[] KEY = "key".getBytes();
	private final byte[] PASS1 = "2709843294721594".getBytes();
	private final byte[] PASS2 = "A5E8F4D8C1B987EFCC00A".getBytes();
	
	private boolean hasFirstKey;
	private boolean hasSecondKey;
	
    public ShutdownBehavior(GreenRuntime runtime) {
		this.channel = runtime.newCommandChannel(NET_RESPONDER);

	}
	    	
	@Override
	public boolean acceptShutdown() {
		return hasFirstKey & hasSecondKey;
	}
	
	@Override
	public boolean restRequest(HTTPRequestReader request) {

		if (request.isEqual(KEY, PASS1)) {
			if (channel.publishHTTPResponse(request, 200)) {
				while(!channel.shutdown()){}
				hasFirstKey = true;
				return true;
			}
		} else if (hasFirstKey && request.isEqual(KEY, PASS2)) {
			if (channel.publishHTTPResponse(request, 200)) {
				hasSecondKey = true;
				return true;
			}
		} else {
			return channel.publishHTTPResponse(request, 404);
		}
		return false;
	}
	
}
```


This class is a simple demonstration of how to use the ```Shutdown()```. This demonstration uses allows for shutdown of a device
