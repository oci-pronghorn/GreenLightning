package com.ociweb.gl.test;

import com.ociweb.gl.api.HTTPResponseReader;

public interface ValidatorFactory {

	boolean validate(long callInstance, HTTPResponseReader reader);
}
