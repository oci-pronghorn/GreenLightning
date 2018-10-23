package com.mydomain.greenlightning.slipstream;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;

import org.h2.tools.Server;

import com.ociweb.gl.api.GreenApp;
import com.ociweb.gl.api.GreenFramework;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.json.JSONRequired;
import com.ociweb.pronghorn.network.HTTPServerConfig;

public class MyDBMicroservice implements GreenApp {

	private final static int maxProductId = 99999;
	
	private static final String BEGIN_TOPIC = "begin";
	private static final String FINISHED_TOPIC = "finish";
	
	private final int port;	
	private final boolean tls;
	private final boolean telemetry;

	private Server server;

	private static String dbURL;
	
	public MyDBMicroservice(boolean tls, int port, boolean telemetry) {
		this.port = port;
		this.tls = tls;
		this.telemetry = telemetry;
		
		//ensure we have a database to test against.
		//this is not needed for production code
		if (dbURL==null) {
			dbURL = startupTestDB();
		}
	}
	
    @Override
    public void declareConfiguration(GreenFramework builder) {

    	HTTPServerConfig c = builder
    	  .useHTTP1xServer(port)
    	  .setMaxConnectionBits(9) 
    	  .setConcurrentChannelsPerDecryptUnit(1)
    	  .setConcurrentChannelsPerEncryptUnit(1)
    	  .setDecryptionUnitsPerTrack(1) //work is slow so only need 1
    	  .setEncryptionUnitsPerTrack(1)
    	  .setHost("127.0.0.1");
    	
    	if (!tls) {
    		c.useInsecureServer();
    	}
    	
        if (telemetry) {
        	builder.enableTelemetry();
        }
        
    	builder
    	  .defineRoute()
    	  .path("/query?id=#{ID}")
    	  .refineInteger("ID", Field.ID, v-> v>=0 & v<=maxProductId) 
    	  .routeId(Struct.PRODUCT_QUERY);
    	
    	builder
	  	  .defineRoute()
	  	  .parseJSON()
	  	    .integerField("id", Field.ID, JSONRequired.REQUIRED, v -> v>=0 & v<=maxProductId)
	  	    .stringField("name", Field.NAME, JSONRequired.REQUIRED, (b,p,l,m) -> l>0 & l<=4000)
	  	    .booleanField("disabled", Field.DISABLED, JSONRequired.REQUIRED)
	  	    .integerField("quantity", Field.QUANTITY, JSONRequired.REQUIRED, v -> v>=0 && v<=1_000_000) //if missing not returning 404? get exception?
	  	  .path("/update")
	  	  .routeId(Struct.PRODUCT_UPDATE);
	  	
    	builder
    	  .defineRoute()
    	  .path("/${path}")
    	  .routeId(Struct.STATIC_PAGES);
    	
    	builder
	  	  .defineRoute()
	  	  .path("/all")
	  	  .routeId(Struct.ALL_PRODUCTS);
    	
    	builder
    	  .defineStruct()
    	  .longField(Field.CONNECTION)
    	  .longField(Field.SEQUENCE)
    	  .integerField(Field.ID)
    	  .integerField(Field.QUANTITY)
    	  .booleanField(Field.DISABLED)
    	  .stringField(Field.NAME)
    	  .register(Struct.DB_PRODUCT_UPDATE);
    	
    	builder
	  	  .defineStruct()
	  	  .longField(Field.CONNECTION)
	  	  .longField(Field.SEQUENCE)
	  	  .integerField(Field.ID)
	  	  .register(Struct.DB_PRODUCT_QUERY);
	    	
    	builder
	  	  .defineStruct()
	  	  .longField(Field.CONNECTION)
	  	  .longField(Field.SEQUENCE)
	  	  .register(Struct.DB_ALL_QUERY);
    	
    	builder
	  	  .defineStruct()
	  	  .longField(Field.CONNECTION)
	  	  .longField(Field.SEQUENCE)
	  	  .integerField(Field.STATUS)
	  	  .stringField(Field.PAYLOAD)
	  	  .register(Struct.RESPONSE);
    	
    	builder.setStartupLimitMS(300);
    }

    @Override
    public void declareBehavior(GreenRuntime runtime) { 
        ProductsBehavior listener = new ProductsBehavior(runtime, maxProductId, BEGIN_TOPIC, server);
		runtime.registerListener(listener)
				.includeRoutes(Struct.PRODUCT_UPDATE, listener::productUpdate)
				.includeRoutes(Struct.ALL_PRODUCTS, listener::productAll)				
                .includeRoutes(Struct.PRODUCT_QUERY, listener::productQuery);
   		
		int threads = 40;		
		
		runtime.registerBlockingListener(new BlockingProducer(dbURL), Field.CONNECTION, 
				                         threads, BEGIN_TOPIC, FINISHED_TOPIC);
		
		runtime.addPubSubListener(new RestResponder(runtime)).addSubscription(FINISHED_TOPIC);
		
		runtime.addResourceServer("/site","index.html").includeRoutesByAssoc(Struct.STATIC_PAGES);
		
    }
    
	
	public String startupTestDB() {

		String url = null;
		try {
			
			File f = new File(System.getProperty("user.home","~")+"/test.mv.db");
			System.out.println(f);
			if (f.exists()) {
				f.delete();//we want to start with a fresh database
			}
			
			//startup the SQL server so we can show how to communicate with it 
			server = Server.createTcpServer().start();
			
			url = "jdbc:h2:"+server.getURL()+"/~/test";
			System.out.println("Server running at: "+url);
			
			Connection connection = DriverManager.getConnection(url, "sa", "");
            connection.setAutoCommit(false);
            Statement stmt = connection.createStatement();
            stmt.execute("CREATE TABLE PRODUCT(id int primary key, quantity int, name varchar(255), disabled boolean)");
            stmt.close();
            connection.commit();
            
            connection.setAutoCommit(false);

            /////
            //add some default products
            /////
            
            PreparedStatement insertPreparedStatement = connection.prepareStatement("INSERT INTO PRODUCT(id, quantity, name, disabled) values(?,?,?,?)");
            insertPreparedStatement.setInt(1, 2);
            insertPreparedStatement.setInt(2, 200);            
			insertPreparedStatement.setString(3, "hammer");
			insertPreparedStatement.setBoolean(4, false);			
            insertPreparedStatement.executeUpdate();           
            connection.commit();
            
            insertPreparedStatement.setInt(1, 7);
            insertPreparedStatement.setInt(2, 400);            
			insertPreparedStatement.setString(3, "wrench");
			insertPreparedStatement.setBoolean(4, false);			
            insertPreparedStatement.executeUpdate();
            connection.commit();
            
            insertPreparedStatement.close();  
            connection.close();
	
		} catch (Exception e) {
			e.printStackTrace();
		}
		return url;	
		        
		
	}
}
