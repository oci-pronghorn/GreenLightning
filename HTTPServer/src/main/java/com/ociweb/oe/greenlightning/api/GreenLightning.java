package com.ociweb.oe.greenlightning.api;

import com.ociweb.gl.api.GreenRuntime;

public class GreenLightning {

	public static void main(String[] args) {
		GreenRuntime.run(new HTTPServer(8088,System.out, 8098));
	}
	
}
