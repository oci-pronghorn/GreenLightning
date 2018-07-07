package com.ociweb.gl.impl;

public interface ChildClassScannerVisitor<T> {
	public boolean visit(T child, Object topParent, String topName);
}
