package com.mydomain.greenlightning.slipstream;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.pronghorn.network.ClientSocketReaderStage;
import com.ociweb.pronghorn.network.ServerSocketReaderStage;
import com.ociweb.pronghorn.network.ServerSocketWriterStage;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;

public class GreenLightning {

	public static void main(String[] args) {		
		GreenRuntime.run(new MyProxy(false),args);
	}
	
}
