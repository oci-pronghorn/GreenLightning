package com.ociweb.oe.greenlightning.api;


import com.ociweb.gl.api.GreenApp;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.Builder;

public class Startup implements GreenApp
{
    @Override
    public void declareConfiguration(Builder c) {

    }

    @Override
    public void declareBehavior(GreenRuntime runtime) {

    	runtime.addStartupListener(()->{
    		System.out.println("Hello, this message will display once at start");
    	});
    }
}
