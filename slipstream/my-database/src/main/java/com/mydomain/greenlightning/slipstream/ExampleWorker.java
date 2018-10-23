package com.mydomain.greenlightning.slipstream;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.ociweb.json.encode.JSONRenderer;
import com.ociweb.pronghorn.pipe.DataInputBlobReader;
import com.ociweb.pronghorn.pipe.DataOutputBlobWriter;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.RawDataSchema;
import com.ociweb.pronghorn.pipe.StructuredReader;
import com.ociweb.pronghorn.pipe.StructuredWriter;
import com.ociweb.pronghorn.stage.blocking.BlockingWorker;
import com.ociweb.pronghorn.util.AppendableBuilder;

public class ExampleWorker implements BlockingWorker {

	private final JSONRenderer<ExampleWorker> renderSelected = new JSONRenderer<ExampleWorker>()
			.startObject()
			.integer("id", o -> o.id )
			.integer("quantity", o-> o.quantity )
			.string("name", (o,t)-> t.append(o.name) )
			.bool("disabled", o->o.disabled)
			.endObject();

	int id;
	int quantity;
	boolean disabled;
	String name;
	
	private final Connection conn;
	
	private final PreparedStatement insertPreparedStatement;
	private final PreparedStatement allQueryPreparedStatement;
	private final PreparedStatement idQueryPreparedStatement;
	
	private AppendableBuilder builder = new AppendableBuilder(1<<20);
	
	public ExampleWorker(String url) {
	
		try {
			conn = DriverManager.getConnection(url, "sa", "");
			insertPreparedStatement   = conn.prepareStatement("MERGE INTO PRODUCT(id, quantity, name, disabled) values(?,?,?,?)");
			allQueryPreparedStatement = conn.prepareStatement("SELECT * FROM PRODUCT");
			idQueryPreparedStatement  = conn.prepareStatement("SELECT * FROM PRODUCT WHERE id=?");
		
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
		
	}
	

	@Override
	public void doWork(Pipe<RawDataSchema> input, Pipe<RawDataSchema> output) {

		input.takeMsgIdx(input);
		DataInputBlobReader<RawDataSchema> reader = input.openInputStream(input);
		
		
		StructuredReader struct = reader.structured();
		long connectionId = struct.readLong(Field.CONNECTION);
		long sequenceId = struct.readLong(Field.SEQUENCE);				
		
		Struct operation = struct.structAssociatedObject();
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

		input.confirmLowLevelRead(input, Pipe.sizeOf(input, RawDataSchema.MSG_CHUNKEDSTREAM_1));
		input.releaseReadLock(input);
				
		////////////////////////
		////////////////////////
		
	
		int status = 200;
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
		///////////////////////////
	    ////////////////////////////
		
		int size = Pipe.addMsgIdx(output, RawDataSchema.MSG_CHUNKEDSTREAM_1);
		DataOutputBlobWriter<RawDataSchema> writer = Pipe.openOutputStream(output);
		
	
		StructuredWriter structOut = writer.structured();
		//write data
		structOut.writeLong(Field.CONNECTION, connectionId);
		structOut.writeLong(Field.SEQUENCE, sequenceId);				
		structOut.writeInt(Field.STATUS, status);
	
		//builder.copyTo(struct.writeText(Field.PAYLOAD)); //is this broken?
		structOut.writeText(Field.PAYLOAD, builder.toString());
		
		structOut.selectStruct(Struct.RESPONSE);
	
		writer.closeLowLevelField();
		Pipe.confirmLowLevelWrite(output, size);
		Pipe.publishWrites(output);
		
	
	}

}
