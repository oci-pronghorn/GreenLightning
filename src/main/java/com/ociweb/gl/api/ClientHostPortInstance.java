package com.ociweb.gl.api;

import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.json.JSONExtractorCompleted;
import com.ociweb.pronghorn.network.ClientCoordinator;
import com.ociweb.pronghorn.util.TrieParser;

public class ClientHostPortInstance {
	private final static AtomicInteger sessionCounter = new AtomicInteger(0);

	private final static Logger logger = LoggerFactory.getLogger(ClientHostPortInstance.class);
	
	final String host;
	final int port;
	public final int sessionId;
	final int hostId;
	final byte[] hostBytes;
	final JSONExtractorCompleted extractor;
	
	//cache
	private long connectionId=-1;

	//TODO: add method to limit headers
	private final TrieParser headers = null; //

	/**
	 *
	 * @param host String arg specifying host
	 * @param port int arg specifying port number
	 */
	public ClientHostPortInstance(String host, int port) {
		this(host, port, sessionCounter.incrementAndGet(),null);
	}


	/**
	 *
	 * @param host String arg specifying host
	 * @param port int arg specifying port number
	 * @param extractor optional JSON extractor
	 */
	public ClientHostPortInstance(String host, int port, JSONExtractorCompleted extractor) {
		//NOTE: session counter values are each unique to the instance and found incrementing in a block for use in array lookups...
		this(host, port, sessionCounter.incrementAndGet(), extractor);
	}

	/**
	 *
	 * @param host String arg specifying host
	 * @param port int arg specifying port number
	 * @param sessionId int arg specifying the sessionId, this is internal an private
	 * @param extractor optional JSON extractor
	 */
	private ClientHostPortInstance(String host, int port, int sessionId, JSONExtractorCompleted extractor) {
		this.host = host;
		this.port = port;
		if (port<=0 || port>65535) {
			throw new UnsupportedOperationException("Invalid port "+port+" must be postive and <= 65535");
		}
		if (sessionId<=0) {
			throw new UnsupportedOperationException("SessionId must be postive and greater than zero. found: "+sessionId);
		}
		this.sessionId = sessionId;
		this.hostId = ClientCoordinator.registerDomain(host);
		this.hostBytes = host.getBytes();
		this.extractor = extractor;
	}

	public JSONExtractorCompleted jsonExtractor() {
		return extractor;
	}
	
	/**
	 * Used to make host and port num a string
	 * @return host and port num.
	 */
	public String toString() {
		return host+":"+port;
	}

	/**
	 * To set the connection id for the session
	 * @param id number to use for connection id
	 */
	void setConnectionId(long id) {
		connectionId = id;		
	}

	/**
	 * To get the connection id for the session
	 * @return long connectionId
	 */
	long getConnectionId() {
		return connectionId;
	}

	/**
	 * @return int sessionCount
	 */
	public static int getSessionCount() {
		return sessionCounter.get();
	}

	public boolean isFor(String host, int port) {
		return (this.host.equals(host)) && (this.port==port);
	}
}
