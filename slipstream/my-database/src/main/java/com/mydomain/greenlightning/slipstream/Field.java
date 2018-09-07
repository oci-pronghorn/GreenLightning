package com.mydomain.greenlightning.slipstream;

/**
 * All the fields for the application are defined here which also provides
 * a great place to store domain knowledge of each field for future developers.
 * 
 * By using the ** (double star) comments before each field this will become part of the help 
 * where it can be seen by developers when they hover over the field name.
 * 
 */
public enum Field {
	/**
	 * Product name which should be understood by a shopper.<br>
	 * Names should be shorter than 4000 chars.
	 */
	NAME, 
	
	/**
	 * Unique identification number (positive) for this product.
	 */
	ID,
	
	/**
	 * Total quantity on hand for this product.<br>
	 * This value can be negative when product has been promised but the quantity is not available.
	 */
	QUANTITY, 
	
	/**
	 * This product is not to be sold at this time.<br>
	 * There may be many positive and negative reasons why a product must not be sold at this time.
	 */
	DISABLED,
 
	/**
	 * Long connection id from the client to the server.<br>
	 * This value is required in order to respond to this <br>
	 * particular user at the appropriate time,<br>
	 */
	CONNECTION,
	
	/**
	 * Long sequence number for this particular request from the client<br>
	 * In http 1.x the order of responses must match that of the requests
	 * and this value is required in order to match the spec since many
	 * operations can be completed in parallel.
	 */
	SEQUENCE,
	
	/**
	 * HTTP status code defined by the specification.<br>
	 * 200 is OK
	 */
	STATUS, 
	
	/**
	 * Block of JSON to be sent back to the client
	 */
	PAYLOAD
}
