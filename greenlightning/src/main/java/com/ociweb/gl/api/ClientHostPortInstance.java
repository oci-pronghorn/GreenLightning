package com.ociweb.gl.api;

import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.json.JSONExtractorCompleted;
import com.ociweb.pronghorn.network.ClientCoordinator;

public class ClientHostPortInstance {
	private static final AtomicInteger sessionCounter = new AtomicInteger(0);	

	
	private final static Logger logger = LoggerFactory.getLogger(ClientHostPortInstance.class);
	
	public final String host;
	public final byte[] hostBytes;
	public final int sessionId;
	
	final int port;
	final int hostId;
	final JSONExtractorCompleted extractor;	
	
	//cache for the active connection
	private long connectionId = -1;
	private long prevConnectionId = -1;

	
	/**
	 *
	 * @param host String arg specifying host
	 * @param port int arg specifying port number
	 * @param extractor optional JSON extractor
	 */
	public ClientHostPortInstance(String host, int port, JSONExtractorCompleted extractor, long callTimeoutNS) {
		//NOTE: session counter values are each unique to the instance and found incrementing in a block for use in array lookups...
		this(host, port, sessionCounter.incrementAndGet(), extractor, callTimeoutNS);
	}
	
	/**
	 *
	 * @param host String arg specifying host
	 * @param port int arg specifying port number
	 * @param sessionId int arg specifying the sessionId, this is internal an private
	 * @param extractor optional JSON extractor
	 */
	private ClientHostPortInstance(String host, int port, int sessionId, 
			                       JSONExtractorCompleted extractor, long callTimeoutNS) {
		this.host = host;
		this.port = port;
		if (port<=0 || port>65535) {
			throw new UnsupportedOperationException("Invalid port "+port+" must be postive and <= 65535");
		}
		if (sessionId<=0) {
			throw new UnsupportedOperationException("SessionId must be postive and greater than zero. found: "+sessionId);
		}
		this.sessionId = sessionId;
		this.hostBytes = host.getBytes();
		this.extractor = extractor;
		
		this.hostId = ClientCoordinator.registerDomain(host); //TODO: this static is bad plus we need to reg the chpis
		
		ClientCoordinator.setSessionTimeoutNS(sessionId, callTimeoutNS);
		
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
		if (id!=connectionId) {
			assert(-1!=id) : "Can not clear connectionId, only set a new one.";
			prevConnectionId = connectionId;
			connectionId = id;		
		}
	}
	
	
	/**
	 * To get the connection id for the session
	 * @return long connectionId
	 */
	public long getConnectionId() {
		return connectionId;
	}

	public long getPrevConnectionId() {
		return prevConnectionId;
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
