package com.ociweb.gl.api;

public enum WaitFor {

	None(false,0),
	All(true,WaitFor.numerator),
	Half(true,WaitFor.numerator/2),
	One(false,1),
	Two(false,2),
	Three(false,3);
	
	public static final int numBits = 6;
	public static final int numerator = 1<<numBits;
	
	private byte policy; //0-64
	
	WaitFor(boolean isPercent, int value) {
		assert(value<=numerator);
		policy = (byte)((isPercent? 128 : 0) | value);
	}

	public int policy() {
		return policy;
	}
	
	public static final int computeRequiredCount(int policy, int totalCount) {
		
		if ((128&policy) == 0) {
			//numbers
			return Math.min(totalCount, (127&policy));
		} else {
			//percent
			return (totalCount*(127&policy))>>numBits;
		}
		
	}
	
}
