package com.ociweb.oe.greenlightning.api;


import com.ociweb.gl.api.GreenApp;
import com.ociweb.gl.api.GreenFramework;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.pronghorn.util.AppendableProxy;
import com.ociweb.pronghorn.util.Appendables;

public class Startup implements GreenApp
{
	
	private final AppendableProxy console;
	
	public Startup(Appendable console) {
		this.console = Appendables.proxy(console);
	}
	
	
    @Override
    public void declareConfiguration(GreenFramework c) {

    }

    @Override
    public void declareBehavior(GreenRuntime runtime) {

    	runtime.addStartupListener(()->{
    		console.append("Hello, this message will display once at start\n");
    		//now we shutdown the app
    		runtime.shutdownRuntime();
    	});
    	
    	
    }
}
