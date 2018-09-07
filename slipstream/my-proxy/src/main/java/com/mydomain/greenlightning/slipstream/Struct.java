package com.mydomain.greenlightning.slipstream;

/**
 * All the structures for the application are defined here which also provides
 * a great place to store domain knowledge of each field for future developers.
 * 
 * By using the ** (double star) comments before each field this will become part of the help 
 * where it can be seen by developers when they hover over the struct name.
 * 
 */
public enum Struct {
	
	/**
	 * Public route for hitting the proxy from external clients.
	 */
	PROXY_ROUTE, 
	
	/**
	 * Route for hitting the internal service which sits behind the proxy.
	 */
	SERIVCE_ROUTE

}
