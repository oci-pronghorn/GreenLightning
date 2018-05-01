package com.ociweb.gl.api;

import com.ociweb.json.JSONExtractor;
import com.ociweb.pronghorn.network.ClientCoordinator;
import com.ociweb.pronghorn.util.TrieParser;

import java.util.concurrent.atomic.AtomicInteger;

public class ClientHostPortInstance {
	private final static AtomicInteger sessionCounter = new AtomicInteger(0);

	final String host;
	final int port;
	public final int sessionId;
	final int hostId;
	final byte[] hostBytes;
	final JSONExtractor extractor;
	
	//cache
	private long connectionId=-1;

	//TODO: add method to limit headers
	private final TrieParser headers = null; //

	/**
	 *
	 * @param host String arg specifying host
	 * @param port int arg specifying port number
	 * @param sessionId int arg specifying the sessionId, null to auto-compute
	 * @param extractor optional JSON extractor
	 */
	public ClientHostPortInstance(String host, int port, Integer sessionId, JSONExtractor extractor) {
		this.host = host;
		this.port = port;
		if (port<=0 || port>65535) {
			throw new UnsupportedOperationException("Invalid port "+port+" must be postive and <= 65535");
		}
		this.sessionId = sessionId != null ? sessionId : sessionCounter.incrementAndGet();
		this.hostId = ClientCoordinator.registerDomain(host);
		this.hostBytes = host.getBytes();
		this.extractor = extractor;
	}

	/**
	 *
	 * @return host and port num.
	 */
	public String toString() {
		return host+":"+port;
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
