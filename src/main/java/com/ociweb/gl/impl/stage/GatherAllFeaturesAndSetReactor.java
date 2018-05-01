package com.ociweb.gl.impl.stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.gl.api.MsgCommandChannel;
import com.ociweb.gl.impl.ChildClassScannerVisitor;
import com.ociweb.gl.impl.schema.TrafficOrderSchema;
import com.ociweb.pronghorn.pipe.Pipe;

class GatherAllFeaturesAndSetReactor implements ChildClassScannerVisitor<MsgCommandChannel> {
//TODO: mere this with other walkers for a single deep scan and faster startup.
	private static final Logger logger = LoggerFactory.getLogger(GatherAllFeaturesAndSetReactor.class);
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

	/**
	 *
	 * @param cmdChnl MsgCommandChannel arg used in MsgCommandChannel
	 * @param topParent Object arg

	 */
	   public boolean visit(MsgCommandChannel cmdChnl, Object topParent) {	
			if (MsgCommandChannel.isGoPipe(cmdChnl, target)) {
				features |= cmdChnl.initFeatures;
			}
			return true;//keep going
	   }

	/**
	 *
	 * @return features
	 */
	public int features() {
		   return features;
	   }
	
}
