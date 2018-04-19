package com.ociweb.gl.impl.http.server;

import com.ociweb.gl.api.HTTPServerConfig;
import com.ociweb.gl.impl.BridgeConfigStage;
import com.ociweb.pronghorn.network.NetGraphBuilder;
import com.ociweb.pronghorn.network.ServerConnectionStruct;
import com.ociweb.pronghorn.network.ServerPipesConfig;
import com.ociweb.pronghorn.network.TLSCertificates;
import com.ociweb.pronghorn.network.schema.HTTPRequestSchema;
import com.ociweb.pronghorn.pipe.PipeConfig;
import com.ociweb.pronghorn.pipe.PipeConfigManager;
import com.ociweb.pronghorn.struct.StructRegistry;

public class HTTPServerConfigImpl implements HTTPServerConfig {
	
	private String defaultHostPath = "";
	private String bindHost = null;
	private int bindPort = -1;
	private int maxConnectionBits = 12;//default of 4K
	private int encryptionUnitsPerTrack = 1; //default of 1 per track or none without TLS
	private int decryptionUnitsPerTrack = 1; //default of 1 per track or none without TLS
	private int concurrentChannelsPerEncryptUnit = 1; //default 1, for low memory usage
	private int concurrentChannelsPerDecryptUnit = 1; //default 1, for low memory usage
	private TLSCertificates serverTLS;
	private BridgeConfigStage configStage = BridgeConfigStage.Construction;
	private int maxRequestSize = 1<<9;//default of 512 bytes
	private final PipeConfigManager pcm;
	
	private final ServerConnectionStruct scs;
	
	public HTTPServerConfigImpl(int bindPort, 
			                    PipeConfigManager pcm, 
			                    StructRegistry recordTypeData) {
		this.bindPort = bindPort;
		if (bindPort<=0 || (bindPort>=(1<<16))) {
			throw new UnsupportedOperationException("invalid port "+bindPort);
		}
		this.defaultHostPath = "";
		this.serverTLS = TLSCertificates.defaultCerts;

		this.bindHost = null;
		this.maxConnectionBits = 12;
		this.pcm = pcm;
		this.scs = new ServerConnectionStruct(recordTypeData);

	}
	
	public ServerConnectionStruct connectionStruct() {
		return scs;
	}

	public void beginDeclarations() {
		this.configStage = BridgeConfigStage.DeclareConnections;
	}
	
	public final int getMaxConnectionBits() {
		return maxConnectionBits;
	}

	public final int getEncryptionUnitsPerTrack() {
		return encryptionUnitsPerTrack;
	}

	public final int getDecryptionUnitsPerTrack() {
		return decryptionUnitsPerTrack;
	}

	public final int getConcurrentChannelsPerEncryptUnit() {
		return concurrentChannelsPerEncryptUnit;
	}

	public final int getConcurrentChannelsPerDecryptUnit() {
		return concurrentChannelsPerDecryptUnit;
	}
	
	public final boolean isTLS() {
		return serverTLS != null;
	}

	public final TLSCertificates getCertificates() {
		return serverTLS;
	}

	public final String bindHost() {
		assert(null!=bindHost) : "finalizeDeclareConnections() must be called before this can be used.";
		return bindHost;
	}

	public final int bindPort() {
		return bindPort;
	}

	public final String defaultHostPath() {
		return defaultHostPath;
	}

	@Override
	public HTTPServerConfig setMaxRequestSize(int maxRequestSize) {
		this.maxRequestSize = maxRequestSize;
		return this;
	}
	
	
	@Override
	public HTTPServerConfig setDefaultPath(String defaultPath) {
		configStage.throwIfNot(BridgeConfigStage.DeclareConnections);
		assert(null != defaultPath);
		this.defaultHostPath = defaultPath;
		return this;
	}

	@Override
	public HTTPServerConfig setHost(String host) {
		configStage.throwIfNot(BridgeConfigStage.DeclareConnections);
		this.bindHost = host;
		return this;
	}

	@Override
	public HTTPServerConfig setTLS(TLSCertificates certificates) {
		configStage.throwIfNot(BridgeConfigStage.DeclareConnections);
		assert(null != certificates);
		this.serverTLS = certificates;
		return this;
	}

	@Override
	public HTTPServerConfig useInsecureServer() {
		configStage.throwIfNot(BridgeConfigStage.DeclareConnections);
		this.serverTLS = null;
		return this;
	}

	@Override
	public HTTPServerConfig setMaxConnectionBits(int bits) {
		configStage.throwIfNot(BridgeConfigStage.DeclareConnections);
		if (bits<1) {
			throw new UnsupportedOperationException("Must support at least 1 connection");
		}
		if (bits>30) {
			throw new UnsupportedOperationException("Can not support "+(1<<bits)+" connections");
		}
		
		this.maxConnectionBits = bits;
		return this;
	}

	@Override
	public HTTPServerConfig setEncryptionUnitsPerTrack(int value) {
		configStage.throwIfNot(BridgeConfigStage.DeclareConnections);
		this.encryptionUnitsPerTrack = value;
		return this;
	}

	@Override
	public HTTPServerConfig setDecryptionUnitsPerTrack(int value) {
		configStage.throwIfNot(BridgeConfigStage.DeclareConnections);
		this.decryptionUnitsPerTrack = value;
		return this;
	}

	@Override
	public HTTPServerConfig setConcurrentChannelsPerEncryptUnit(int value) {
		configStage.throwIfNot(BridgeConfigStage.DeclareConnections);
		this.concurrentChannelsPerEncryptUnit = value;
		return this;
	}

	@Override
	public HTTPServerConfig setConcurrentChannelsPerDecryptUnit(int value) {
		configStage.throwIfNot(BridgeConfigStage.DeclareConnections);
		this.concurrentChannelsPerDecryptUnit = value;
		return this;
	}

	public void finalizeDeclareConnections() {
		this.bindHost = NetGraphBuilder.bindHost(this.bindHost);
		this.configStage = BridgeConfigStage.DeclareBehavior;
	}
	
	@Override
	public ServerPipesConfig buildServerConfig(int tracks) {
		
		int incomingMsgFragCount = defaultComputedChunksCount();

		pcm.addConfig(new PipeConfig<HTTPRequestSchema>(HTTPRequestSchema.instance, 
				Math.max(incomingMsgFragCount-2, 2), 
				getMaxRequestSize()));
				
		return new ServerPipesConfig(
				isTLS(),
				getMaxConnectionBits(),
		   		tracks,
				getEncryptionUnitsPerTrack(),
				getConcurrentChannelsPerEncryptUnit(),
				getDecryptionUnitsPerTrack(),
				getConcurrentChannelsPerDecryptUnit(),				
				//one message might be broken into this many parts
				incomingMsgFragCount,
				getMaxRequestSize());
		
	}

	private int defaultComputedChunksCount() {
		return 2+(getMaxRequestSize()/1500);
	}

	public int getMaxRequestSize() {
		return this.maxRequestSize;
	}
}
