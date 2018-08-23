package com.ociweb.gl.impl.stage;

import com.ociweb.gl.impl.schema.TrafficOrderSchema;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.stage.PronghornStage;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;

public class ReactiveProxyStage extends PronghornStage {

	private final ReactiveProxy proxy;
	
	protected ReactiveProxyStage(ReactiveProxy proxy, GraphManager graphManager, Pipe input, Pipe output) {
		super(graphManager, input, output);
		this.proxy = proxy;
		//GraphManager.addNota(graphManager, GraphManager.SCHEDULE_RATE, 20_000, this); //slows the apply operation logic
	}
	
	protected ReactiveProxyStage(ReactiveProxy proxy, GraphManager graphManager, Pipe[] input, Pipe output) {
		super(graphManager, input, output);
		this.proxy = proxy;
		//GraphManager.addNota(graphManager, GraphManager.SCHEDULE_RATE, 20_000, this); //slows the apply operation logic
	}
	
	protected ReactiveProxyStage(ReactiveProxy proxy, GraphManager graphManager, Pipe input, Pipe[] output) {
		super(graphManager, input, output);
		this.proxy = proxy;
		//.addNota(graphManager, GraphManager.SCHEDULE_RATE, 20_000, this); //slows the apply operation logic
	}
	
	protected ReactiveProxyStage(ReactiveProxy proxy, GraphManager graphManager, Pipe[] input, Pipe[] output) {
		super(graphManager, input, output);
		this.proxy = proxy;
		//GraphManager.addNota(graphManager, GraphManager.SCHEDULE_RATE, 20_000, this); //slows the apply operation logic
	}

	
	@Override
	public void startup() {
		proxy.startup();
	}
	
	@Override
	public void run() {
		proxy.run();
	}

	@Override
	public void shutdown() {
		proxy.shutdown();
	}

	public int getFeatures(Pipe<TrafficOrderSchema> orderPipe) {
		return proxy.getFeatures(orderPipe);
	}

	public void didWork() {
		this.didWorkMonitor.published();
	}
	
}
