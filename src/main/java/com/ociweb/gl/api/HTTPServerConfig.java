package com.ociweb.gl.api;

import com.ociweb.pronghorn.network.ServerConnectionStruct;
import com.ociweb.pronghorn.network.ServerPipesConfig;
import com.ociweb.pronghorn.network.TLSCertificates;

public interface HTTPServerConfig {
	HTTPServerConfig setDefaultPath(String defaultPath);
	HTTPServerConfig setHost(String host);
	HTTPServerConfig setTLS(TLSCertificates certificates);
	HTTPServerConfig useInsecureServer();
	HTTPServerConfig setMaxConnectionBits(int bits);
	HTTPServerConfig setMaxRequestSize(int maxRequestSize);
	HTTPServerConfig setEncryptionUnitsPerTrack(int value);
	HTTPServerConfig setDecryptionUnitsPerTrack(int value);
	HTTPServerConfig setConcurrentChannelsPerEncryptUnit(int value);
	HTTPServerConfig setConcurrentChannelsPerDecryptUnit(int value);
	HTTPServerConfig logTraffic(String basePath, int fileCount, long fileSizeLimit);
	HTTPServerConfig logTraffic();
	
	int getMaxConnectionBits();

	int getEncryptionUnitsPerTrack();

	int getDecryptionUnitsPerTrack();

	int getConcurrentChannelsPerEncryptUnit();

	int getConcurrentChannelsPerDecryptUnit();

	boolean isTLS();

	TLSCertificates getCertificates();
	
	ServerConnectionStruct connectionStruct();

	String bindHost();

	int bindPort();

	String defaultHostPath();
	
	int getMaxRequestSize();
		
	ServerPipesConfig buildServerConfig(int tracks);

}

