package com.ociweb.gl.impl.stage;

import com.ociweb.gl.api.MsgCommandChannel;
import com.ociweb.gl.impl.ChildClassScannerVisitor;
import com.ociweb.gl.impl.schema.TrafficOrderSchema;
import com.ociweb.pronghorn.pipe.Pipe;

class GatherAllFeatures implements ChildClassScannerVisitor<MsgCommandChannel> {

	   private Pipe<TrafficOrderSchema> target;
	   private int features;
	   
	   public void init(Pipe<TrafficOrderSchema> target) {
		   this.target = target;
		   this.features = 0;
	   }
	   
	   public boolean visit(MsgCommandChannel cmdChnl, Object topParent) {
			if (cmdChnl.isGoPipe(target)) {
				features |= cmdChnl.initFeatures;
			}
			return true;//keep going
	   }
	
	   public int features() {
		   return features;
	   }
	
}
