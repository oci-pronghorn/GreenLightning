package com.ociweb;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.pronghorn.HTTPServer;
import com.ociweb.pronghorn.network.NetGraphBuilder;
import com.ociweb.pronghorn.network.http.ModuleConfig;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;

public class GreenLightning {
	//$ java -jar phogLite.jar --s ../src/main/resources/site


	static final Logger logger = LoggerFactory.getLogger(GreenLightning.class);
	
	public static void main(String[] args) {
						
		String path = HTTPServer.getOptArg("-site", "--s", args, null);	
		String resourceRoot = HTTPServer.getOptArg("-resourcesRoot", "--rr", args, null==path?"/site/index.html":null);
		String rootFolder = null;
		
		if (null==path) {
		   if (null==resourceRoot) {
			   System.out.println("Path to site must be defined with -site or --s");			   
			   return;			
		   } else {
			   //use internal resources	
			   
			   int endOfRoot = resourceRoot.lastIndexOf('/');
			   if (-1==endOfRoot) {
				   System.out.println("resourceRoot must contain at least one / to define the subfolder inside the resources folder");
				   return;
			   }
			   rootFolder = resourceRoot.substring(0, endOfRoot);
			   			   
			   System.out.println("reading site data from internal resources: "+rootFolder);  
		   }			
		} else {
			   if (null==resourceRoot) {
				   //normal file path site
				   System.out.println("reading site data from: "+path);
			   } else {
				   System.out.println("use -size for file paths or -resourcesRoot for packaged resources. Only one can be used at a time.");
				   return;
			   }
		}
		
		
		String isTLS = HTTPServer.getOptArg("-tls", "--t", args, "True");	
		String isLarge = HTTPServer.getOptArg("-large", "--l", args, "False");	

		String strPort = HTTPServer.getOptArg("-port", "--p", args, "8080");
		int port = Integer.parseInt(strPort);
		
		String bindHost = HTTPServer.getOptArg("-host", "--h", args, null);
		
	    boolean large = Boolean.parseBoolean(isLarge);
	    
	    if (null==bindHost) {
		    bindHost = bindHost();		
	    }
	   	
	    final int fileOutgoing = large ? 2048 : 1024;//makes big performance difference.
	    final int fileChunkSize = large? 1<<14 : 1<<10;
	    
		GraphManager gm = new GraphManager();
		HTTPServer.startupHTTPServer(gm, large, 
				GreenLightning.simpleModuleConfig(path, resourceRoot, rootFolder, fileOutgoing, fileChunkSize), bindHost, port, Boolean.parseBoolean(isTLS) );
        		
		System.out.println("Press \"ENTER\" to exit...");
		int value = -1;
		do {
		    try {
		        value = System.in.read();
		    } catch (IOException e) {
		        e.printStackTrace();
		    }
		} while (value!=10);
	    System.exit(0);
		
	}

    @Deprecated //use NetGraphBuilder.bindHost
	public static String bindHost() {
		String bindHost;
		boolean noIPV6 = true;//TODO: we really do need to add ipv6 support.
		List<InetAddress> addrList = NetGraphBuilder.homeAddresses(noIPV6);
		if (addrList.isEmpty()) {
			bindHost = "127.0.0.1";
		} else {
			bindHost = addrList.get(0).toString().replace("/", "");
		}
		return bindHost;
	}

	
    public static ModuleConfig simpleModuleConfig(String path, String resourceRoot, String rootFolder,
    		                         final int fileOutgoing, final int fileChunkSize) {
    	
    	
    	//GreenLightning.class.getClassLoader().getResourceAsStream(name)
    	
    		
    	File tempPathRoot = null;
		if (null!=path) {
			tempPathRoot = new File(path.replace("target/phogLite.jar!",""));
			if (tempPathRoot.exists()) {
				logger.info("reading files from folder {}",tempPathRoot);
			} else {
				logger.info("EXITING: unable to find {}",tempPathRoot);
				System.exit(-1);				
			}
		}
		
		final String resourcesRoot = resourceRoot;
		final String resourcesDefault = rootFolder;		
		final File pathRoot = tempPathRoot;
		
		return HTTPServer.simpleFileServerConfig(
				fileOutgoing, fileChunkSize, 
				resourcesRoot, resourcesDefault, 
				pathRoot);
	}

	
}
