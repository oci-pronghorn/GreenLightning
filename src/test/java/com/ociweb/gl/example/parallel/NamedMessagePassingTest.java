package com.ociweb.gl.example.parallel;

import org.junit.Ignore;
import org.junit.Test;

import com.ociweb.gl.api.GreenRuntime;

public class NamedMessagePassingTest {

	@Ignore
	public void runTest() {
		
		GreenRuntime.run(new NamedMessagePassingApp());
		
//		try {
//			Thread.sleep(120_000);
//		} catch (InterruptedException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
		
	}
	
}
