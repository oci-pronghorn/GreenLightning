package com.mydomain.greenlightning.slipstream;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;

import org.h2.tools.Server;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPRequestReader;
import com.ociweb.gl.api.PubSubFixedTopicService;
import com.ociweb.gl.api.RestMethodListener;
import com.ociweb.gl.api.ShutdownListener;
import com.ociweb.gl.api.StartupListener;
import com.ociweb.pronghorn.pipe.StructuredReader;
import com.ociweb.pronghorn.pipe.StructuredWriter;

public class ProductsBehavior implements RestMethodListener, StartupListener, ShutdownListener {

	private final PubSubFixedTopicService publishService;	
	private Server server;//held so we can shut down later.

	
	public ProductsBehavior(GreenRuntime runtime, int maxProductId, String topic) {
			
		publishService = runtime.newCommandChannel().newPubSubService(topic);

	}
		
	public boolean productUpdate(HTTPRequestReader request) {
		StructuredReader source = request.structured();
		
		return publishService.publishTopic((w)->{
			
			//write update message with new payload plus sequence and connectionID
			StructuredWriter target = w.structured();
			target.writeInt(Field.ID, source.readInt(Field.ID));
			target.writeInt(Field.QUANTITY, source.readInt(Field.QUANTITY));
			target.writeBoolean(Field.DISABLED, source.readBoolean(Field.DISABLED));
			
			//source.readText(Field.NAME, target.writeText(Field.NAME)); //Broken??
			target.writeText(Field.NAME, source.readText(Field.NAME));
			
			
			target.writeLong(Field.CONNECTION, request.getConnectionId());
			target.writeLong(Field.SEQUENCE, request.getSequenceCode());
			target.selectStruct(Struct.DB_PRODUCT_UPDATE);

		});
	}
	
	public boolean productQuery(HTTPRequestReader request) {
		StructuredReader source = request.structured();
		
		return publishService.publishTopic((w)->{
			
			StructuredWriter target = w.structured();
			target.writeInt(Field.ID, source.readInt(Field.ID));
			target.writeLong(Field.CONNECTION, request.getConnectionId());
			target.writeLong(Field.SEQUENCE, request.getSequenceCode());
			target.selectStruct(Struct.DB_PRODUCT_QUERY);
			
		});
	
	}
	
	public boolean productAll(HTTPRequestReader request) {

		return publishService.publishTopic((w)->{
			
			StructuredWriter target = w.structured();
			target.writeLong(Field.CONNECTION, request.getConnectionId());
			target.writeLong(Field.SEQUENCE, request.getSequenceCode());
			target.selectStruct(Struct.DB_ALL_QUERY);
			
		});

	}

	@Override
	public void startup() {

		try {
			
			File f = new File(System.getProperty("user.home","~")+"/test.mv.db");
			System.out.println(f);
			if (f.exists()) {
				f.delete();//we want to start with a fresh database
			}
			
			//startup the SQL server so we can show how to communicate with it 
			server = Server.createTcpServer().start();
			
			final String dbURL = "jdbc:h2:"+server.getURL()+"/~/test";
			System.out.println("Server running at: "+dbURL);
			
			Connection connection = DriverManager.getConnection(dbURL, "sa", "");
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
			
			//send URL to stages where we need it
			publishService.presumePublishTopic((w)->{
				w.writeUTF(dbURL);
			});
		} catch (Exception e) {
			e.printStackTrace();
		}
			
		
	        
		
	}


	@Override
	public boolean acceptShutdown() {
		server.shutdown();
		return true;
	}
	
}
