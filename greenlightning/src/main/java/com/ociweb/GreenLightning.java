package com.ociweb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.gl.api.GreenApp;
import com.ociweb.gl.api.GreenFramework;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.pronghorn.HTTPServer;
import com.ociweb.pronghorn.network.HTTPServerConfig;
import com.ociweb.pronghorn.network.TLSCertificates;
import com.ociweb.pronghorn.network.TLSCerts;

public class GreenLightning {
	
	static final Logger logger = LoggerFactory.getLogger(GreenLightning.class);
	
	public static void main(String[] args) {
						
		String path = HTTPServer.getOptArg("--site", "-s", args, "/usr/share/greenlightning/html/index.html");	
		String strTLS = HTTPServer.getOptArg("--tls", "-t", args, "True");
		boolean isTLS = Boolean.parseBoolean(strTLS);
		
		String strPort = HTTPServer.getOptArg("--port", "-p", args, "8080");
		int port = Integer.parseInt(strPort);
		
		String bindHost = HTTPServer.getOptArg("--host", "-h", args, "0.0.0.0");
		
		String strClientAuthReq = HTTPServer.getOptArg("--clientAuth", "-c", args, "False");
		boolean isClientAuthRequired = Boolean.parseBoolean(strClientAuthReq);

		String identityStoreResourceName = HTTPServer.getOptArg("--storeResource", "-store", args, null);
        String keyPassword = HTTPServer.getOptArg("--keyPassword", "-keyPass", args, null);
        String keyStorePassword = HTTPServer.getOptArg("--keyStorePassword", "-storePass", args, null);

		String strTrustAll = HTTPServer.getOptArg("--trustAll", "-a", args, "True");
		boolean trustAll = Boolean.parseBoolean(strTrustAll);
		
		String strTelemetryPort = HTTPServer.getOptArg("--telemetryPort", "-m", args, "-1");
		int telemetryPort = Integer.parseInt(strTelemetryPort);
		
		GreenRuntime.run(
				new StaticFileServer(
							isTLS, 
							bindHost, 
							port, 
							path,
							isClientAuthRequired,
							identityStoreResourceName,
							keyPassword,
							keyStorePassword,
							trustAll,
							telemetryPort
						));
	
	}

	public static class StaticFileServer implements GreenApp{

		private final int bindPort;
		private final String bindHost;
		private final String filePath;
		private final int connectionBits = 10;
		private final boolean isTLS;
		private final boolean isClientAuthRequired;
		private final String identityStoreResourceName;
		private final String keyPassword;
		private final String keyStorePassword;
		private final boolean trustAll;
		private final int telemetryPort;
		
		public enum ROUTE_ID {
			ROOT
		};
		
		public StaticFileServer(boolean isTLS, String bindHost, int bindPort, String path,
				               boolean isClientAuthRequired, 
				               String identityStoreResourceName,
				               String keyPassword,
				               String keyStorePassword,
				               boolean trustAll, int telemetryPort) {
			this.bindPort = bindPort;
			this.bindHost = bindHost;
			this.filePath = path;
			this.isTLS = isTLS;
			
			this.isClientAuthRequired=isClientAuthRequired;
			
			this.identityStoreResourceName=identityStoreResourceName;
			this.keyPassword=keyPassword;
			this.keyStorePassword=keyStorePassword;
			
			this.trustAll = trustAll;
			this.telemetryPort = telemetryPort;
		}		

		@Override
		public void declareConfiguration(GreenFramework config) {
					
			config.enableTelemetry(telemetryPort);
			
			HTTPServerConfig server = config.useHTTP1xServer(bindPort)
											.setMaxConnectionBits(connectionBits)
										    .setHost(bindHost);
			
			
			if (!isTLS) {
				server.useInsecureServer();
			} else {
				//if any one of these do not match the default then we use custom
				boolean customCerts = isClientAuthRequired | 
						              (identityStoreResourceName!=null) |
						              (keyPassword!=null) |
						              (keyStorePassword!=null) |
						              (!trustAll);
				if (customCerts) {
					
					TLSCerts temp = TLSCerts.define();
					
					temp.clientAuthRequired(isClientAuthRequired);
					
					if (null!=identityStoreResourceName) {
						temp.identityStoreResourceName(identityStoreResourceName);
					}
					if (null!=keyPassword) {
						temp.keyPassword(keyPassword);
					}
					if (null!=keyStorePassword) {
						temp.keyStorePassword(keyStorePassword);
					}					
					if (trustAll) {
						temp.trustAll();
					}
					
					server.setTLS(temp);
				} else {
					server.setTLS(TLSCertificates.defaultCerts); 
				}
			}
		
			config.defineRoute()
			      .path("${path}")
			      .routeId(ROUTE_ID.ROOT);

		}

		@Override
		public void declareBehavior(GreenRuntime runtime) {
			
			runtime.addFileServer(filePath).includeRoutesByAssoc(ROUTE_ID.ROOT);
			
		}

	}
	
}
