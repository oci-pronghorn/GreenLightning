package ${package};

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.pronghorn.stage.scheduling.ScriptedNonThreadScheduler;

/**
 * Unit test for simple App.
 */
public class AppTest { 

	
	 @Test
	    public void testApp()
	    {
		    int timeoutMS = 2000;
		    GreenRuntime.testUntilShutdownRequested(new ${artifactId}(), timeoutMS);
		    	    
			
	    }
}
