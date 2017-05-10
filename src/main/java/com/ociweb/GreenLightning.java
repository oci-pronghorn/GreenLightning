package com.ociweb;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.pronghorn.HTTPServer;
import com.ociweb.pronghorn.network.NetGraphBuilder;
import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;
import com.ociweb.pronghorn.network.config.HTTPHeaderDefaults;
import com.ociweb.pronghorn.network.config.HTTPRevisionDefaults;
import com.ociweb.pronghorn.network.config.HTTPSpecification;
import com.ociweb.pronghorn.network.config.HTTPVerbDefaults;
import com.ociweb.pronghorn.network.http.ModuleConfig;
import com.ociweb.pronghorn.network.module.FileReadModuleStage;
import com.ociweb.pronghorn.network.schema.HTTPRequestSchema;
import com.ociweb.pronghorn.network.schema.ServerResponseSchema;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeConfig;
import com.ociweb.pronghorn.pipe.util.hash.IntHashTable;
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
		    boolean noIPV6 = true;//TODO: we really do need to add ipv6 support.
		    List<InetAddress> addrList = NetGraphBuilder.homeAddresses(noIPV6);
			if (addrList.isEmpty()) {
				bindHost = "127.0.0.1";
			} else {
				bindHost = addrList.get(0).toString().replace("/", "");
			}		
	    }
	   	
	    final int fileOutgoing = large ? 2048 : 1024;//makes big performance difference.
	    final int fileChunkSize = large? 1<<14 : 1<<10;
	    
		HTTPServer.startupHTTPServer(large, GreenLightning.moduleConfig(path, resourceRoot, rootFolder, fileOutgoing, fileChunkSize), bindHost, port, Boolean.parseBoolean(isTLS) );
        		
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

	
    static ModuleConfig moduleConfig(String path, String resourceRoot, String rootFolder,
    		                         final int fileOutgoing, final int fileChunkSize) {
    	
    	
    	//GreenLightning.class.getClassLoader().getResourceAsStream(name)
    	
    	
    	
    	final int moduleCount = 1;		
    	final int fileServerIdx = 0;
    	
    	
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
		final int finalModuleCount = 1;
		final int fileServerIndex = fileServerIdx;
		
		//using the basic no-fills API
		ModuleConfig config = new ModuleConfig() {
		
			
		    final PipeConfig<ServerResponseSchema> fileServerOutgoingDataConfig = new PipeConfig<ServerResponseSchema>(ServerResponseSchema.instance, fileOutgoing, fileChunkSize);//from modules  to  supervisor
		
		    //must create here to ensure we have the same instance for both the module and outgoing pipes
		    Pipe<ServerResponseSchema>[][] staticFileOutputs;
		    
			@Override
			public IntHashTable addModule(int a, 
					GraphManager graphManager, Pipe<HTTPRequestSchema>[] inputs,
					HTTPSpecification<HTTPContentTypeDefaults, HTTPRevisionDefaults, HTTPVerbDefaults, HTTPHeaderDefaults> spec) {
				
				//
				if (fileServerIndex == a) {
					
					//the file server is stateless therefore we can build 1 instance for every input pipe
					int instances = inputs.length;
					
					staticFileOutputs = new Pipe[instances][1];
					
					int i = instances;
					while (--i>=0) {
						staticFileOutputs[i][0] = new Pipe<ServerResponseSchema>(fileServerOutgoingDataConfig); //TODO: old code which will be removed.
						if (null != pathRoot) {
							//file based site
							FileReadModuleStage.newInstance(graphManager, inputs[i], staticFileOutputs[i][0], spec, pathRoot);	
						} else {
							//jar resources based site
							FileReadModuleStage.newInstance(graphManager, inputs[i], staticFileOutputs[i][0], spec, resourcesRoot, resourcesDefault);	
						}
					}
					
				}

				//return needed headers
				return IntHashTable.EMPTY;
			}
		
			@Override
			public CharSequence getPathRoute(int a) {
				if (fileServerIndex == a) {
					return "/%b";
				} else {
					return null;
				}
				
				
			}
			
			
			@Override
			public Pipe<ServerResponseSchema>[][] outputPipes(int a) {
				if (fileServerIndex == a) {
					if (null==staticFileOutputs) {
						throw new UnsupportedOperationException("the addModule method must be called first");
					}
					return staticFileOutputs;
				} else {
					return null;
				}
			}
		
			@Override
			public int moduleCount() {
				return finalModuleCount;
			}        
		 	
		 };
		return config;
	}

	
}
