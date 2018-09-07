package com.mydomain.greenlightning.slipstream;

import java.util.Random;

import com.ociweb.pronghorn.util.Appendables;

public class Product {

	public int id;
	public int quantity;
	public boolean disabled;
	public String name;
	
	public Product(int i) {
		
		Random r = new Random(i);
		id = i;
		quantity = Math.abs(r.nextInt(1000));
		disabled = r.nextBoolean();
		byte[] temp = new byte[10];
		r.nextBytes(temp);
		name = Appendables.appendBase64Encoded(new StringBuilder(), temp, 0, temp.length, Integer.MAX_VALUE).toString();

	}

}
