package com.ociweb.gl.benchmark;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPRequestReader;
import com.ociweb.gl.api.HTTPResponseService;
import com.ociweb.gl.api.RestMethodListener;
import com.ociweb.gl.api.TickListener;
import com.ociweb.json.encode.JSONRenderer;
import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;
import com.ociweb.pronghorn.pipe.ObjectPipe;

import io.reactiverse.pgclient.PgClient;
import io.reactiverse.pgclient.PgIterator;
import io.reactiverse.pgclient.PgPool;
import io.reactiverse.pgclient.PgPoolOptions;
import io.reactiverse.pgclient.Tuple;

public class DBUpdate implements RestMethodListener, TickListener {


	private final PgPoolOptions options;
	private PgPool pool;
	
	private final HTTPResponseService service;
	private ObjectPipe<ResultObject> inFlight;	
	private boolean collectionPending = false;	
	private final List<ResultObject> collector = new ArrayList<ResultObject>();
	
	private static final ThreadLocalRandom localRandom = ThreadLocalRandom.current();
	private static final JSONRenderer<List<ResultObject>> multiTemplate = new JSONRenderer<List<ResultObject>>()
	    	  .array((o,i) -> i<o.size()?o:null)
		          .startObject((o, i) -> o.get(i))
					.integer("id", o -> o.getId() )
					.integer("randomNumber", o -> o.getResult())
		          .endObject();
	
	public DBUpdate(GreenRuntime runtime, PgPoolOptions options, int pipelineBits, int maxResponseCount, int maxResponseSize) {
		this.options = options;
		this.service = runtime.newCommandChannel().newHTTPResponseService(maxResponseCount, maxResponseSize);
		this.inFlight = new ObjectPipe<ResultObject>(pipelineBits, ResultObject.class,	ResultObject::new);
	}
	
	private PgPool pool() {
		if (null==pool) {
			pool = PgClient.pool(options);
		}
		return pool;
	}
	
	private int randomValue() {
		return 1+localRandom.nextInt(10000);
	}	
	
	public boolean updateRestRequest(HTTPRequestReader request) {
		int queries;
		if (Struct.UPDATES_ROUTE_INT == request.getRouteAssoc() ) {		
			queries = Math.min(Math.max(1, (request.structured().readInt(Field.QUERIES))),500);		
		} else {
			queries = 1;
		}
		long conId = request.getConnectionId();
		long seqCode = request.getSequenceCode();

		if (inFlight.hasRoomFor(queries)) {		
				    	
				int q = queries;
				while (--q >= 0) {
				
						final ResultObject target = inFlight.headObject();
						assert(null!=target);
					
						target.setConnectionId(conId);
						target.setSequenceId(seqCode);
						target.setStatus(-2);//out for work	
						target.setGroupSize(queries);
						
						final int targetId = randomValue();
						final int targetUpdate = randomValue();
						
						pool().preparedQuery("SELECT * FROM world WHERE id=$1", Tuple.of(targetId), r -> {
								if (r.succeeded()) {
									
									PgIterator resultSet = r.result().iterator();
							        Tuple row = resultSet.next();			        
							        
							        int oldRnd = row.getInteger(1);
							        
							        ///////////////////////////////////
							        //update the value since we got our call back
							        //////////////////////////////////
							        							       
							        
							        final Tuple updateTuple = Tuple.of(targetUpdate, row.getInteger(0));
							        
							        pool().preparedQuery("UPDATE world SET randomnumber=$1 WHERE id=$2", updateTuple, ar -> {
							        	
										if (ar.succeeded()) {									
								        	target.setId(updateTuple.getInteger(1));
								        	target.setResult(updateTuple.getInteger(0));					
								        	target.setStatus(200);
										} else {	
											
											if (ar.cause()!=null) {
												ar.cause().printStackTrace();
											}
											
											target.setStatus(500);
										}	
																													
							        });
								} else {	
									
									if (r.cause()!=null) {
										r.cause().printStackTrace();
									}
									
									target.setStatus(500);
								}		
								
								
							});	
									
						inFlight.moveHeadForward(); //always move to ensure this can be read.
				
				}
				
			return true;
		} else {
			return false;
		}
	}
	
	
	@Override
	public void tickEvent() { 
		
		ResultObject temp = inFlight.tailObject();
		while (isReady(temp)) {			
			if (consumeResultObject(temp)) {
				temp = inFlight.tailObject();
			} else {
				break;
			}
		}	   
		
	}

	private boolean isReady(ResultObject temp) {

		if (collectionPending) {
			//now ready to send, we have all the data	
			if (!publishMultiResponse(collector.get(0).getConnectionId(), collector.get(0).getSequenceId() )) {
				return false;
			}
		}
		
		return null!=temp && temp.getStatus()>=0;
	}

	private boolean consumeResultObject(final ResultObject t) {
		boolean ok;
				
		//collect all the objects
		collector.add(t);
		inFlight.moveTailForward();//only move forward when it is consumed.
		if (collector.size() == t.getGroupSize()) {
			//now ready to send, we have all the data						
			ok =publishMultiResponse(t.getConnectionId(), t.getSequenceId());
		} else {
			ok = true;//added to list
		}				
		
		return ok;
	}

	private boolean publishMultiResponse(long conId, long seqCode) {
		boolean result =  service.publishHTTPResponse(conId, seqCode, 200,
					    				   HTTPContentTypeDefaults.JSON,
					    				   w-> {
					    					   multiTemplate.render(w, collector);
					    					   int c = collector.size();
					    					   while (--c>=0) {
					    						   assert(collector.get(c).getConnectionId() == conId);
					    						   assert(collector.get(c).getSequenceId() == seqCode);					    						   
					    						   collector.get(c).setStatus(-1);
					    					   }
					    					   collector.clear();
					    					   inFlight.publishTailPosition();
					    				   });
		collectionPending = !result;
		return result;
	}
	
	
	
}
