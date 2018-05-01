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
		
		builder.appendPipeMappingIncludingGroupIds(input, parallelIndex, routeIds);
	    
		return this;
	}
	
	@Override
	public RouteFilter includeRoutesByAssoc(Object ... assocRouteObjects) {
		
		int r = assocRouteObjects.length;
		int[] routeIds = new int[r];
		while (--r >= 0) {
			routeIds[r] = builder.routerConfig().lookupRouteIdByIdentity(assocRouteObjects[r]);
		}		
		
		builder.appendPipeMappingIncludingGroupIds(input, parallelIndex, routeIds);
	    
		return this;
	}
	

	@Override
	public RouteFilter excludeRoutes(int... routeIds) {
		
		builder.appendPipeMappingExcludingGroupIds(input, parallelIndex, routeIds);

		return this;
	}


	@Override
	public RouteFilter includeAllRoutes() {
		
		builder.appendPipeMappingAllGroupIds(input, parallelIndex);

		return this;
	}

}
