package com.ociweb.gl.test;

import com.ociweb.pronghorn.network.http.HeaderWritable;

public interface HeaderWritableFactory {

	HeaderWritable headerWritable(long callInstance);
}
