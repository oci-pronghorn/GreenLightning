package com.ociweb.oe.greenlightning.api;

import com.ociweb.gl.api.Builder;
import com.ociweb.gl.api.GreenApp;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.pronghorn.util.AppendableProxy;
import com.ociweb.pronghorn.util.Appendables;

public class PubSubStructured implements GreenApp
{
    static int VALUE_FIELD = 1;
    static int SENDER_FIELD = 2;
    
    private AppendableProxy console;
    private AppendableProxy console2;
    
    public PubSubStructured(Appendable console, Appendable console2) {
    	this.console = Appendables.proxy(console);
    	this.console2 = Appendables.proxy(console2);
    }
    
    @Override
    public void declareConfiguration(Builder c) {
    }

    @Override
    public void declareBehavior(GreenRuntime runtime) {

        runtime.addStartupListener(new KickoffBehavior(runtime, "topicOne"));

        runtime.addPubSubListener(new IncrementValueBehavior(runtime, "topicTwo", console))
                                                  .addSubscription("topicOne");

        runtime.addPubSubListener(new IncrementValueBehavior(runtime, "topicOne", console))
                                                  .addSubscription("topicTwo");

        runtime.addPubSubListener(new FilterValueBehavior(runtime, "filteredValues"))
                                                  .addSubscription("topicOne")
                                                  .addSubscription("topicTwo");
                
        runtime.addPubSubListener(new ConsoleWrite(console2))
        										  .addSubscription("filteredValues");
        
    
    }
}
