package com.ociweb.gl.api;

import com.ociweb.gl.impl.BuilderImpl;
import com.ociweb.pronghorn.network.schema.HTTPRequestSchema;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeConfig;

public class ListenerConfig {

    private final static Pipe[] EMPTY_PIPES = new Pipe[0];
    
    private Pipe<HTTPRequestSchema>[] restRequests = EMPTY_PIPES;
	
        
	public ListenerConfig(BuilderImpl builder, int[] routes, int parallelInstance) {

	
			assert(routes.length>0) : "API requires 1 or more routes";
			
			///////////
			//use a single pipe to consume all the rout requests to ensure they remain in order
			///////////
			PipeConfig<HTTPRequestSchema> pipeConfig = builder.restPipeConfig.grow2x();
			
			int count;
			if (-1 == parallelInstance) {
				count = builder.parallelism();
			} else {
				count = 1;
			}
			restRequests = new Pipe[count];
			int i = count;
			while (--i>=0) {
				Pipe<HTTPRequestSchema> pipe = restRequests[i] = builder.newHTTPRequestPipe(pipeConfig);
				int r = routes.length;
				while (--r >= 0) {
					//map all these routes to the same pipe
					builder.recordPipeMapping(pipe, routes[r], i);
				}
			}
			
		
			/////////////////
			//old
			/////////////////
//			
//			int r = routes.length;
//					
//			int p;
//			if (-1 == parallelInstance) {
//				p = builder.parallelism();
//			} else {
//				p = 1;
//			}
//			int count = r*p;
//			
//			restRequests = new Pipe[count];
//			
//			int idx = count;
//			
//			while (--r >= 0) {	
//				int routeIndex = routes[r];
//				//if parallel is -1 we need to do one for each
//							
//				int x = p;
//				while (--x>=0) {
//								
//					Pipe<HTTPRequestSchema> pipe = builder.newHTTPRequestPipe(pipeConfig);				
//					builder.recordPipeMapping(pipe, routeIndex, parallelInstance);
//					restRequests[--idx] = pipe;
//		            
//				}            
//			}
	
	}
	


    public Pipe<HTTPRequestSchema>[] getHTTPRequestPipes() {
    	return restRequests;
    }

}
