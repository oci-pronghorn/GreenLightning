package com.ociweb.gl.impl.stage;

import com.ociweb.gl.api.CommandChannelVisitor;
import com.ociweb.gl.api.MsgCommandChannel;
import com.ociweb.gl.impl.schema.TrafficOrderSchema;
import com.ociweb.pronghorn.pipe.Pipe;

class CommandChannelWithMatchingPipe implements CommandChannelVisitor {

	   private Pipe<TrafficOrderSchema> target;
	   private int features;
	   
	   public void init(Pipe<TrafficOrderSchema> target) {
		   this.target = target;
		   this.features = 0;
	   }
	   
	   public void visit(MsgCommandChannel cmdChnl) {
			if (cmdChnl.isGoPipe(target)) {
				features = cmdChnl.initFeatures;
			}
	   }
	
	   public int features() {
		   return features;
	   }
	
}
