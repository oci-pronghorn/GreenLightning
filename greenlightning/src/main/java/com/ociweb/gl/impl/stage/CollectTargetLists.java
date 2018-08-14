package com.ociweb.gl.impl.stage;

import com.ociweb.pronghorn.util.ByteSquenceVisitor;

import java.util.Arrays;

public class CollectTargetLists implements ByteSquenceVisitor {

	private int[] targetLists;
	private int collectedIndex;
	
	public CollectTargetLists(int[] subscriberLists, int collectedIndex) {
		this.targetLists = subscriberLists;
		this.collectedIndex = collectedIndex;//offset found after all the data we need to keep
	}

	public void reset(int[] subscriberLists, int collectedIndex) {
		this.targetLists = subscriberLists;
		this.collectedIndex=collectedIndex;
	}
	
	@Override
	public void addToResult(long value) {
		
		if (collectedIndex >= targetLists.length) {
			//grow array as needed
			int[] temp = new int[collectedIndex*2];
			Arrays.fill(temp, -1);
			System.arraycopy(targetLists, 0, temp, 0, targetLists.length);
			targetLists = temp;
		}
		
		targetLists[collectedIndex++] = (int)value; //keep value
		
	}

	/**
	 *
	 * @return targetLists
	 */
	public int[] targetArray() {
		//ensure we end with -1 then return
		targetLists[collectedIndex++] = -1;
		return targetLists;
	}

}
