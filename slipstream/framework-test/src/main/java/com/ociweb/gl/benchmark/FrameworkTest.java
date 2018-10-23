package com.ociweb.gl.benchmark;

import com.ociweb.gl.api.GreenApp;
/**
 * ************************************************************************
 * For greenlightning support, training or feature reqeusts please contact:
 *   info@objectcomputing.com   (314) 579-0066
 * ************************************************************************
 */
import com.ociweb.gl.api.GreenFramework;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.pronghorn.network.ServerSocketWriterStage;

import io.reactiverse.pgclient.PgClient;
import io.reactiverse.pgclient.PgPool;
import io.reactiverse.pgclient.PgPoolOptions;

public class FrameworkTest implements GreenApp {

	static final String payloadText="Hello, World!";
	static final byte[] payload = payloadText.getBytes();

	private int bindPort;
    private String host;
    private int concurrentWritesPerChannel;
    private int queueLengthOfPendingRequests;
    private int telemetryPort;//for monitoring
    private int minMemoryOfInputPipes;
	
    private final PgPoolOptions options;
    
	public static int connectionsPerTrack = 2;
	public static String url = "jdbc:postgresql://tfb-database:5432/hello_world";
	public static String user = "benchmarkdbuser";
	public static String pass = "benchmarkdbpass";
	
	//  jdbc:postgresql://tfb-database:5432/hello_world
	// database: hello_world
	// username: benchmarkdbuser
	// password: benchmarkdbpass
	//
	// poolSize: 256/tracks
	// no tls
	// cache prepared statements
	// NOTE: thread creation for this pool must be careful to create those with very small stacks/heaps...
	
	//To use the PostgreSQL mode, use the database URL jdbc:h2:~/test;MODE=PostgreSQL
	//  or the SQL statement SET MODE PostgreSQL.
	// user pass/  "sa", ""
	
    
    public FrameworkTest() {
    	//this server works best with  -XX:+UseNUMA    	
    	this(System.getProperty("host","0.0.0.0"), 
    		 8080,    //default port for test 
    		 10,      //default concurrency, 10 to support 280 channels on 14 core boxes
    		 8*1024, //default max rest requests allowed to queue in wait
    		 1<<21,   //default network buffer per input socket connection
    		 Integer.parseInt(System.getProperty("telemetry.port", "-1")));
    	
    //	RunLocalDB.buildH2Table();
//    	
//		//This url does not turn on the server but instead uses the embedded system.
//		//FrameworkTest.url = "jdbc:h2:~/test;MODE=PostgreSQL";
//		//FrameworkTest.url = "jdbc:postgresql://tfb-database:5432/hello_world";
		FrameworkTest.url =  //"jdbc:h2:tcp://127.0.1.1:5432/hello_world"; 
		                   //"jdbc:h2:tcp://127.0.1.1:5432/~/hello_world";
				              "jdbc:postgresql://localhost/testdb";//test??
		FrameworkTest.user = "postgres";
		FrameworkTest.pass = "postgres";
		///RunLocalDB.populateTargetDB();
//		
//		
//		System.out.println("Connecting to server running at: "+FrameworkTest.url);
//		
	


				
				
    	
    }
   
    
    public FrameworkTest(String host, int port, 
    		             int concurrentWritesPerChannel, 
    		             int queueLengthOfPendingRequests, 
    		             int minMemoryOfInputPipes,
    		             int telemetryPort) {
    	this.bindPort = port;
    	this.host = host;
    	this.concurrentWritesPerChannel = concurrentWritesPerChannel;
    	this.queueLengthOfPendingRequests = queueLengthOfPendingRequests;
    	this.minMemoryOfInputPipes = minMemoryOfInputPipes;
    	this.telemetryPort = telemetryPort;
    	
    	options = new PgPoolOptions()
    			.setPort(5432)
    			.setPipeliningLimit(1<<16)
    			.setTcpFastOpen(true)
    			.setHost("localhost")
    			.setDatabase("testdb")
    			.setUser("postgres")
    			.setPassword("postgres")
    			.setCachePreparedStatements(true)
    			.setMaxSize(1);//connectionsPerTrack);


    }

    
	@Override
    public void declareConfiguration(GreenFramework framework) {
		
		//TODO: hack 14 for testing.
		framework.useHTTP1xServer(bindPort, 14, this::parallelBehavior) //standard auto-scale
    			 .setHost(host)
    			 .setConcurrentChannelsPerDecryptUnit(concurrentWritesPerChannel)
    			 .setConcurrentChannelsPerEncryptUnit(concurrentWritesPerChannel)
    			 .setMaxQueueIn(queueLengthOfPendingRequests)
    			 .setMinimumInputPipeMemory(minMemoryOfInputPipes)
    			 .setMaxQueueOut(1<<7)//8)//12) ///TODO: value??
    			 .setMaxResponseSize(12_000) //what is payload size for reponses?
    	         .useInsecureServer(); //turn off TLS

		
		framework.defineRoute()
		         .path("/plaintext")
		         .routeId(Struct.PLAINTEXT_ROUTE);
		
		framework.defineRoute()
		        .path("/json")
		        .routeId(Struct.JSON_ROUTE);
		
		framework.defineRoute()
		        .path("/db")
		        .routeId(Struct.DB_SINGLE_ROUTE);
		
		framework.defineRoute()
		        .path("/queries?queries=#{queries}")
		        .path("/queries")
		        .refineInteger("queries", Field.QUERIES, 1)
		        .routeId(Struct.DB_MULTI_ROUTE);
			
		framework.defineStruct()
		         .longField(Field.CONNECTION)
		         .longField(Field.SEQUENCE)
		         .register(Struct.DB_SINGLE_REQUEST);
		        		
		
		if (telemetryPort>0) {
			framework.enableTelemetry(host,telemetryPort);
		}
		
		ServerSocketWriterStage.lowLatency = false; //turn on high volume mode, less concerned about low latency. (enum?)
	
    }

	public void parallelBehavior(GreenRuntime runtime) {

//		Vertx vertx = Vertx.vertx(new VertxOptions().
//				  setPreferNativeTransport(true)
//		);

		SimpleRest restTest = new SimpleRest(runtime);
		
		runtime.registerListener("Simple", restTest)
		       .includeRoutes(Struct.PLAINTEXT_ROUTE, restTest::plainRestRequest)
		       .includeRoutes(Struct.JSON_ROUTE, restTest::jsonRestRequest);

		
	 
		//each track has its own pool with its own async thread
		PgPool pool = PgClient.pool(options);
		
		DBRest dbRestInstance = new DBRest(runtime, pool);
		runtime.registerListener("DBRest", dbRestInstance)
				.includeRoutes(Struct.DB_SINGLE_ROUTE, dbRestInstance::singleRestRequest)
				.includeRoutes(Struct.DB_MULTI_ROUTE, dbRestInstance::multiRestRequest);		

	}
	 
    @Override
    public void declareBehavior(GreenRuntime runtime) { 
    }
  
}
