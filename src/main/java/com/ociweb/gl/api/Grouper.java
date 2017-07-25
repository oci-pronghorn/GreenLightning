package com.ociweb.gl.api;

import com.ociweb.pronghorn.pipe.MessageSchema;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeConfig;
import com.ociweb.pronghorn.stage.route.ReplicatorStage;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;

public class Grouper {

	private final Pipe[] inputPipes;
	private final Pipe[][] groupedPipes;
	private int count;
	
	private Pipe[] first; //these will be the ones for the behavior.
	
	public Grouper(Pipe[] catagories) {
		this.inputPipes = catagories;
		this.groupedPipes = new Pipe[catagories.length][0];
	}

	public int additions() {
		return count;
	}
	
	public PipeConfig config(MessageSchema schema) {
		int i = inputPipes.length;
		while (--i>=0) {
			if (Pipe.isForSchema(inputPipes[i], schema)) {
				return inputPipes[i].config();
			}
		}
		throw new UnsupportedOperationException("can not find "+schema);
	}
	
	
	public void add(Pipe[] pipes) {
		if (0==count) {
			first = pipes;
		}
		count++;
		int i = pipes.length;
		while (--i>=0) {
			Pipe p = pipes[i];
			int j = inputPipes.length;
			while (--j>=0) {
				if (Pipe.isForSameSchema(inputPipes[j], p)) {
			      
					Pipe[] targetArray = groupedPipes[j];
					Pipe[] newArray = new Pipe[targetArray.length+1];
					System.arraycopy(targetArray, 0, newArray, 0, targetArray.length);
					newArray[targetArray.length] = p;
					groupedPipes[j] = newArray;
					return;
				}
			}
		}
	}
	
	
	public Pipe[] firstArray() {
		return first;
	}

	public void buildReplicators(GraphManager gm) {
		int i = inputPipes.length;
		while (--i>=0) {
			ReplicatorStage.newInstance(gm, inputPipes[i], groupedPipes[i]);
		}
	}
	

}
