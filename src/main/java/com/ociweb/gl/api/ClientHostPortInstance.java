package com.ociweb.gl.api;

import com.ociweb.json.JSONExtractor;
import com.ociweb.pronghorn.network.ClientCoordinator;
import com.ociweb.pronghorn.util.TrieParser;

import java.util.concurrent.atomic.AtomicInteger;

public class ClientHostPortInstance {

	public static AtomicInteger sessionCounter = new AtomicInteger(0);
	
	public final String host;
	public final int hostId;
	public final byte[] hostBytes;
	public final int port;
	public final int sessionId; 
	public final TrieParser headers = null; //
	
	public final JSONExtractor extractor;
	
	//cache
	private long connectionId=-1;

	//TODO: add method to limit headers


	/**
	 *
	 * @return host and port num.
	 */
	public String toString() {
		return host+":"+port;
	}

	/**
	 * @param host String arg specifying host
	 * @param port int arg specifying port number
	 * @param sessionId int arg specifying the sessionId
	 */
	public ClientHostPortInstance(String host, int port, int sessionId) {
		this(host,port,null,sessionId);
	}

	/**
	 * @param host String arg specifying host
	 * @param port int arg specifying port number
	 */
	public ClientHostPortInstance(String host, int port) {
		this(host,port,null);
	}

	/**
	 *
	 * @param host String arg specifying host
	 * @param port int arg specifying port number
	 * @param extractor JSON data to be extracted
	 * @param sessionId int arg specifying the sessionId
	 */
	public ClientHostPortInstance(String host, int port, JSONExtractor extractor, int sessionId) {
		this.sessionId = sessionCounter.incrementAndGet();
		this.host = host;
		this.port = port;
		if (port<=0 || port>65535) {
			throw new UnsupportedOperationException("Invalid port "+port+" must be postive and <= 65535");
		}
		this.extractor = null;
		this.hostId = ClientCoordinator.registerDomain(host);
		this.hostBytes = host.getBytes();

	}

	/**
	 * @param host String arg specifying host
	 * @param port int arg specifying port number
	 * @param extractor JSON data to be extracted
	 */
	public ClientHostPortInstance(String host, int port, JSONExtractor extractor) {
		this.sessionId = sessionCounter.incrementAndGet();
		this.host = host;
		this.hostId = ClientCoordinator.registerDomain(host);
		this.hostBytes = host.getBytes();
		this.port = port;
		if (port<=0 || port>65535) {
			throw new UnsupportedOperationException("Invalid port "+port+" must be postive and <= 65535");
		}
		this.extractor = null;
	}

	/**
	 *
	 * @param id number to use for connection id
	 */
	void setConnectionId(long id) {
		connectionId = id;		
	}

	/**
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
	
}
