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
import com.ociweb.gl.api.HTTPSession;

public class HTTPClient implements GreenApp
{

    @Override
    public void declareConfiguration(Builder c) {
    	//c.useInsecureNetClient();
    	c.useNetClient();

    }

    @Override
    public void declareBehavior(GreenRuntime runtime) {       
    	
    	HTTPSession session = new HTTPSession(
    			//"javanut.com",80,0);
    			"127.0.0.1",8088,0);
    	
    	HTTPGetBehaviorSingle temp = new HTTPGetBehaviorSingle(runtime, session);
		runtime.addStartupListener(temp).addSubscription("next");
			   	
		//HTTPSession session = new HTTPSession("127.0.0.1",8088,0);
    	//runtime.addResponseListener(new HTTPResponse()).includeHTTPSession(session);    	
    	//runtime.addStartupListener(new HTTPGetBehaviorChained(runtime, session));
    	    	
    	
    	runtime.addPubSubListener(new ShutdownBehavior(runtime)).addSubscription("shutdown");
    	
    }
          
}
```

Behavior class:

```java
package com.ociweb.oe.greenlightning.api;

import com.ociweb.gl.api.HTTPSession;
import com.ociweb.gl.api.StartupListener;
import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenRuntime;

public class HTTPGetBehaviorChained implements StartupListener {
	
	private GreenCommandChannel cmd;
	private int responseId;
    private HTTPSession session;
	
	public HTTPGetBehaviorChained(GreenRuntime runtime, HTTPSession session) {
		this.cmd = runtime.newCommandChannel(NET_REQUESTER);
		this.session = session;
	}

	@Override
	public void startup() {
		
		cmd.httpGet(session, "/testPageB");
		
	}

}
```


```java
package com.ociweb.oe.greenlightning.api;

import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPResponseListener;
import com.ociweb.gl.api.HTTPResponseReader;
import com.ociweb.gl.api.HTTPSession;
import com.ociweb.gl.api.Payloadable;
import com.ociweb.gl.api.PubSubListener;
import com.ociweb.gl.api.StartupListener;
import com.ociweb.gl.api.TimeListener;
import com.ociweb.pronghorn.pipe.ChannelReader;
import com.ociweb.pronghorn.util.Appendables;

public class HTTPGetBehaviorSingle implements StartupListener, HTTPResponseListener, PubSubListener {

	
	private final GreenCommandChannel cmd;
	private HTTPSession session;
	 
	public HTTPGetBehaviorSingle(GreenRuntime runtime, HTTPSession session) {
		this.session = session;
		cmd = runtime.newCommandChannel(NET_REQUESTER | DYNAMIC_MESSAGING);
	}


	@Override
	public void startup() {
	    cmd.publishTopic("next");
	}
	
	long d = 0;
	long c = 0;

	@Override
	public boolean responseHTTP(HTTPResponseReader reader) {
		
		long duration = System.nanoTime()-reqTime;
		
		d+=duration;
		c+=1;
		
		if(0==(0xFFF&c)) {//running average
			Appendables.appendNearestTimeUnit(System.err, d/c, " latency\n");
		}
		
	//	System.out.println(" status:"+reader.statusCode());
	//	System.out.println("   type:"+reader.contentType());
		
		Payloadable payload = new Payloadable() {
			@Override
			public void read(ChannelReader reader) {
				String readUTFOfLength = reader.readUTFOfLength(reader.available());
				//System.out.println(readUTFOfLength);
			}
		};
		
		reader.openPayloadData( payload );
		
		cmd.publishTopic("next");
		
		return true;
	}


	int countDown = 4000;
	long reqTime = 0;

	@Override
	public boolean message(CharSequence topic, ChannelReader payload) {
		
		if (--countDown<=0) {
			cmd.httpGet(session, "/shutdown?key=shutdown");
			cmd.publishTopic("shutdown");
		}
		
		reqTime = System.nanoTime();
		return cmd.httpGet(session, "/testPageB");

	}

}
```


```java
package com.ociweb.oe.greenlightning.api;

import com.ociweb.gl.api.HTTPResponseListener;
import com.ociweb.gl.api.HTTPResponseReader;
import com.ociweb.gl.api.Payloadable;
import com.ociweb.pronghorn.pipe.ChannelReader;

public class HTTPResponse implements HTTPResponseListener {

	@Override
	public boolean responseHTTP(HTTPResponseReader reader) {
		
		System.out.println(" status:"+reader.statusCode());
		System.out.println("   type:"+reader.contentType());

		Payloadable payload = new Payloadable() {
			@Override
			public void read(ChannelReader reader) {
				System.out.println(reader.readUTFOfLength(reader.available()));
			}
		};
		boolean hadAbody = reader.openPayloadData(payload );

		
		return true;
	}

}
```


