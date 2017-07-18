package com.ociweb.gl.api;

import com.ociweb.gl.impl.BuilderImpl;
import com.ociweb.pronghorn.network.schema.HTTPRequestSchema;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeConfig;

public class ListenerConfig {


        
	public static void recordPipeMapings(BuilderImpl builder, int[] routes, int parallelInstance,
			Pipe<HTTPRequestSchema>[] restRequests) {
		int r;
		int idx = restRequests.length;
		
		final int p = ListenerConfig.computeParallel(builder, parallelInstance);
		
		int x = p;
		while (--x>=0) {	
			int parallelId = -1 == parallelInstance ? x : parallelInstance;
			Pipe<HTTPRequestSchema> pipe = restRequests[--idx];
			r = routes.length;
			while (--r >= 0) {	
				int routeIndex = routes[r];
				builder.appendPipeMapping(pipe, routeIndex, parallelId);          
			}            
		}
	}

	public static Pipe<HTTPRequestSchema>[] newHTTPRequestPipes(BuilderImpl builder, final int parallelInstance) {
		Pipe<HTTPRequestSchema>[] restRequests = new Pipe[parallelInstance];
		
		
		PipeConfig<HTTPRequestSchema> pipeConfig = builder.restPipeConfig.grow2x();
		int idx = restRequests.length;
		while (--idx>=0) {
			Pipe<HTTPRequestSchema> pipe = builder.newHTTPRequestPipe(pipeConfig);
			restRequests[idx] = pipe;
		}
		return restRequests;
	}

	public static int computeParallel(BuilderImpl builder, int parallelInstance) {
		final int p;
		if (-1 == parallelInstance) {
			p = builder.parallelism();
		} else {
			p = 1;
		}
		return p;
	}
	


}
