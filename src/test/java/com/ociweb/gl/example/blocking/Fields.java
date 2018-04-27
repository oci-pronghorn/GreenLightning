package com.ociweb.gl.example.blocking;

import com.ociweb.pronghorn.struct.FieldIdxHolder;

public enum Fields implements FieldIdxHolder
{key1, key2, connectionId, sequenceId;

private long fieldIdx;	
@Override
public long fieldIdx() {
	return fieldIdx;
}

@Override
public void fieldIdx(long idx) {
	fieldIdx = idx;
}}