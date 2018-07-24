package com.ociweb.pronghorn.util;

import java.lang.reflect.Array;

public class ArrayGrow {

	public static int[] appendToArray(int[] array, int newItem) {
		
		int i = array.length;
		int[] newArray = new int[i+1];
		System.arraycopy(array, 0, newArray, 0, i);
		newArray[i] = newItem;
		return newArray;
	}

	public static <T> T[] appendToArray(T[] source, T append) {
		
		int i = source.length;
		T[] newArray = (T[]) Array.newInstance(append.getClass(), i+1);
		System.arraycopy(source, 0, newArray, 0, i);
		newArray[i] = append;
		return newArray;
	}

	
	public static boolean[] setIntoArray(boolean[] source, boolean obj, int pos) {
		
		int i = source.length;
		if (pos>=i) {
			int newSize = i>0 ? i*2 : 2;
			if (pos>=newSize) {
				newSize = pos>0 ? pos*2 : 2;
			}
			boolean[] newArray = new boolean[newSize];
			System.arraycopy(source, 0, newArray, 0, i);
			source = newArray;
			
		}
		source[pos] = obj;
		return source;
	}

	public static <T> T[] setIntoArray(T[] source, T obj, int pos) {
		
		int i = source.length;
		if (pos>=i) {
			int newSize = i>0 ? i*2 : 2;
			if (pos>=newSize) {
				newSize = pos>0 ? pos*2 : 2;
			}
			T[] newArray = (T[]) Array.newInstance(obj.getClass(), newSize);
			System.arraycopy(source, 0, newArray, 0, i);
			source = newArray;
			
		}
		source[pos] = obj;
		return source;
	}
	
	
	
}
