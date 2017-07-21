package com.ociweb.gl.api;

import com.ociweb.gl.impl.BuilderImpl;
import com.ociweb.pronghorn.network.schema.HTTPRequestSchema;
import com.ociweb.pronghorn.pipe.Pipe;

public class StageRouteFilter implements RouteFilter {

	private final Pipe<HTTPRequestSchema> input;
    private final int parallelIndex;
    private final BuilderImpl builder;
    
	public StageRouteFilter(Pipe<HTTPRequestSchema> input, BuilderImpl builder, int parallelIndex) {
		this.input = input;
		this.builder = builder;
		this.parallelIndex = parallelIndex;
	}
		
	@Override
	public RouteFilter includeRoutes(int... routeIds) {
	    //  RouteFilter
		int j = routeIds.length;
		while(--j>=0) {
			builder.appendPipeMapping(input, routeIds[j], parallelIndex);			
		}
		
		return this;
	}

	@Override
	public RouteFilter includeAllRoutes() {
		
		//  RouteFilter
		final int count = Math.max(1, builder.routerConfig().routesCount());
		int j = count;
		while(--j>=0) {
			builder.appendPipeMapping(input, j, parallelIndex);			
		}
		
		return this;
	}

}
