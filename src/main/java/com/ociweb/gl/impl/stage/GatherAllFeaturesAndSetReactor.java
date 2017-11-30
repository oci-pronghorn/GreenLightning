package com.ociweb.gl.impl.stage;

import com.ociweb.gl.api.MsgCommandChannel;
import com.ociweb.gl.impl.ChildClassScannerVisitor;
import com.ociweb.gl.impl.schema.TrafficOrderSchema;
import com.ociweb.pronghorn.pipe.Pipe;

class GatherAllFeaturesAndSetReactor implements ChildClassScannerVisitor<MsgCommandChannel> {

	private final ReactiveListenerStage<?> reactiveListenerStage;
	
	public GatherAllFeaturesAndSetReactor(ReactiveListenerStage<?> reactiveListenerStage) {
		this.reactiveListenerStage = reactiveListenerStage;
	}

	private Pipe<TrafficOrderSchema> target;
	   private int features;
	   
	   public void init(Pipe<TrafficOrderSchema> target) {
		   this.target = target;
		   this.features = 0;
	   }
	   
	   public boolean visit(MsgCommandChannel cmdChnl, Object topParent) {
		   
		   //TODO: get the object for lookup and set it
		    cmdChnl.setPrivateTopics(reactiveListenerStage.publishPrivateTopics);
		   
			if (cmdChnl.isGoPipe(target)) {
				features |= cmdChnl.initFeatures;
			}
			return true;//keep going
	   }
	
	   public int features() {
		   return features;
	   }
	
}
