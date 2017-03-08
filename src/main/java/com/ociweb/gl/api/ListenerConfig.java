package com.ociweb.gl.api;

import com.ociweb.gl.impl.BuilderImpl;
import com.ociweb.pronghorn.network.schema.HTTPRequestSchema;
import com.ociweb.pronghorn.pipe.Pipe;

public class ListenerConfig {

    private final static Pipe[] EMPTY_PIPES = new Pipe[0];
    
    private Pipe<HTTPRequestSchema>[] restRequests = EMPTY_PIPES;
	
        
	public ListenerConfig(BuilderImpl builder, int[] routes, int parallelInstance) {

	
			assert(routes.length>0) : "API requires 1 or more routes";
			
			int r = routes.length;
					
			int p;
			if (-1 == parallelInstance) {
				p = builder.parallelism();
			} else {
				p = 1;
			}
			int count = r*p;
			
			restRequests = new Pipe[count];
			int idx = count;
			
			while (--r >= 0) {	
				int routeIndex = routes[r];
				//if parallel is -1 we need to do one for each
							
				int x = p;
				while (--x>=0) {
								
					Pipe<HTTPRequestSchema> pipe = builder.createHTTPRequestPipe(builder.restPipeConfig.grow2x(), routeIndex, -1 == parallelInstance ? x : parallelInstance);				
					
					restRequests[--idx] = pipe;
		            
				}            
			}
	
	}
	


    Pipe<HTTPRequestSchema>[] getHTTPRequestPipes() {
    	return restRequests;
    }

}
