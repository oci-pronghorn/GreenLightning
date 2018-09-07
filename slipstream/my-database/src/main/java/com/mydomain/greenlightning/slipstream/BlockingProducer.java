package com.mydomain.greenlightning.slipstream;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.ociweb.gl.api.blocking.BlockingBehavior;
import com.ociweb.gl.api.blocking.BlockingBehaviorProducer;
import com.ociweb.json.encode.JSONRenderer;
import com.ociweb.pronghorn.pipe.ChannelReader;
import com.ociweb.pronghorn.pipe.ChannelWriter;
import com.ociweb.pronghorn.pipe.StructuredReader;
import com.ociweb.pronghorn.pipe.StructuredWriter;
import com.ociweb.pronghorn.util.AppendableBuilder;


public class BlockingProducer implements BlockingBehaviorProducer {

	private String dbURL;
	
	private final class SQLBlockingBehavior extends BlockingBehavior {
		private long connectionId;
		private long sequenceId;
		private Struct operation;
		private Connection conn;
		private PreparedStatement insertPreparedStatement;
		private PreparedStatement allQueryPreparedStatement;
		private PreparedStatement idQueryPreparedStatement;
		private AppendableBuilder builder = new AppendableBuilder(1<<20);//init with max possible response.
		private int status;


		private int id;
		private int quantity;
		private boolean disabled;
		private String name;

		private final JSONRenderer<SQLBlockingBehavior> renderSelected = new JSONRenderer<SQLBlockingBehavior>()
						.startObject()
							.integer("id", o -> id )
					    	.integer("quantity", o-> quantity )
							.string("name", (o,t)-> t.append(name) )
							.bool("disabled", o->disabled)
						.endObject();
	
		
		@Override
		public boolean begin(ChannelReader reader) {
				
			if (dbURL==null) {
				System.out.println("new work skipped since we ahve no URL.");
				//do not pick up any work until the db URL is known.
				return false;
			}
			
			StructuredReader struct = reader.structured();
			connectionId = struct.readLong(Field.CONNECTION);
			sequenceId = struct.readLong(Field.SEQUENCE);				
			
			operation = struct.structAssociatedObject();
			switch (operation) {
				case DB_ALL_QUERY:
					break;
				case DB_PRODUCT_QUERY: 
					id = struct.readInt(Field.ID);
					break;
				case DB_PRODUCT_UPDATE:
					id = struct.readInt(Field.ID);
					quantity = struct.readInt(Field.QUANTITY);
					disabled = struct.readBoolean(Field.DISABLED);
					name = struct.readText(Field.NAME);
					break;
			}
			return true;
		}

		@Override
		public void run() throws InterruptedException {
			
			if (conn == null) {
				try {
					conn = DriverManager.getConnection(dbURL, "sa", "");
					insertPreparedStatement   = conn.prepareStatement("MERGE INTO PRODUCT(id, quantity, name, disabled) values(?,?,?,?)");
					allQueryPreparedStatement = conn.prepareStatement("SELECT * FROM PRODUCT");
					idQueryPreparedStatement  = conn.prepareStatement("SELECT * FROM PRODUCT WHERE id=?");
				
				} catch (SQLException e) {
					throw new RuntimeException(e);
				}
			}
			
			status = 200;
			builder.clear(); //empty the payload.				
			switch (operation) {
				case DB_ALL_QUERY:
					try {
							ResultSet rs = allQueryPreparedStatement.executeQuery();					            
							while (rs.next()) {
								if (builder.byteLength()>0) {
									builder.append(",\n");
								}
								id = rs.getInt(1);
								quantity = rs.getInt(2);
								name = rs.getString(3);
								disabled = rs.getBoolean(4);
								
								renderSelected.render(builder, this);
							}								
							rs.close();
					} catch (SQLException e1) {
						e1.printStackTrace();
						status = 500;
					}
					break;
				case DB_PRODUCT_QUERY: 
					
					try {
							idQueryPreparedStatement.setInt(1, id);
							ResultSet rs = idQueryPreparedStatement.executeQuery();			
							if (rs.next()) {
								id = rs.getInt(1);
								quantity = rs.getInt(2);
								name = rs.getString(3);
								disabled = rs.getBoolean(4);
								
								renderSelected.render(builder, this);								
							}
							rs.close();
					} catch (SQLException e1) {
						e1.printStackTrace();
						status = 500;
					}
					break;
				case DB_PRODUCT_UPDATE:			
					try {
						insertPreparedStatement.setInt(1, id);
						insertPreparedStatement.setInt(2, quantity);            
						insertPreparedStatement.setString(3, name);
						insertPreparedStatement.setBoolean(4, disabled);			
						insertPreparedStatement.executeUpdate();           
					} catch (SQLException e) {
						e.printStackTrace();
						status = 500;
					}
					break;
			}
		}

		@Override
		public void finish(ChannelWriter writer) {
			// TODO Auto-generated method stub
			StructuredWriter struct = writer.structured();
			//write data
			struct.writeLong(Field.CONNECTION, connectionId);
			struct.writeLong(Field.SEQUENCE, sequenceId);				
			struct.writeInt(Field.STATUS, status);

			//builder.copyTo(struct.writeText(Field.PAYLOAD)); //is this broken?
			struct.writeText(Field.PAYLOAD, builder.toString());
			
			struct.selectStruct(Struct.RESPONSE);
		}

		@Override
		public void timeout(ChannelWriter writer) {			
			StructuredWriter struct = writer.structured();
			//write data
			struct.writeLong(Field.CONNECTION, connectionId);
			struct.writeLong(Field.SEQUENCE, sequenceId);
			struct.writeInt(Field.STATUS, 504); //timeout
			//no payload response
			struct.selectStruct(Struct.RESPONSE);
		}
	}

	@Override
	public boolean unChosenMessages(ChannelReader reader) {
		dbURL = reader.readUTF();
		return true;
	}
	
	@Override
	public BlockingBehavior produce() {
		return new SQLBlockingBehavior();
	}

}
