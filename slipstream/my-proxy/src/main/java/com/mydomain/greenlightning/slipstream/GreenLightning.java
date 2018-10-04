package com.mydomain.greenlightning.slipstream;

import com.ociweb.gl.api.GreenRuntime;

public class GreenLightning {

	public static void main(String[] args) {		
		GreenRuntime.run(new MyProxy(true),args);
	}
	
}
