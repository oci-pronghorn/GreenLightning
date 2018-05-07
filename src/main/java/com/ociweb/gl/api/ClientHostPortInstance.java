package com.ociweb.gl.api;

import com.ociweb.json.JSONExtractorCompleted;
import com.ociweb.pronghorn.network.ClientCoordinator;
import com.ociweb.pronghorn.util.TrieParser;

import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
	 * @param sessionId int arg specifying the sessionId
	 */
	public ClientHostPortInstance(String host, int port, int sessionId) {
		this(host, port, sessionId, null);
	}

	/**
	 *
	 * @param host String arg specifying host
	 * @param port int arg specifying port number
	 * @param extractor optional JSON extractor
	 */
	public ClientHostPortInstance(String host, int port, JSONExtractorCompleted extractor) {
		this(host, port, sessionCounter.incrementAndGet(), extractor);
	}

	/**
	 *
	 * @param host String arg specifying host
	 * @param port int arg specifying port number
	 * @param sessionId int arg specifying the sessionId
	 * @param extractor optional JSON extractor
	 */
	public ClientHostPortInstance(String host, int port, int sessionId, JSONExtractorCompleted extractor) {
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
