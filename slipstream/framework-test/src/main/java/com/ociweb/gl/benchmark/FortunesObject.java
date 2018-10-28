package com.ociweb.gl.benchmark;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.reactiverse.pgclient.Tuple;

public class FortunesObject {

	private long connectionId; 
	private long sequenceId;
	private int status;
	private List<FortuneObject> list = new ArrayList<FortuneObject>();
	

	public long getConnectionId() {
		return connectionId;
	}


	public void setConnectionId(long connectionId) {
		this.connectionId = connectionId;
	}


	public long getSequenceId() {
		return sequenceId;
	}


	public void setSequenceId(long sequenceId) {
		this.sequenceId = sequenceId;
	}


	public int getStatus() {
		return status;
	}


	public void setStatus(int status) {
		this.status = status;
	}


	public void clear() {
		list.clear();
	}

	public void sort() {
		Collections.sort(list);
	}

	public void addFortune(int id, String fortune) {
		
		FortuneObject obj = new FortuneObject(); //TODO: if we need recycle else not...
		obj.setId(id);
		obj.setFortune(fortune);
		list.add(obj);

	}

		
}
