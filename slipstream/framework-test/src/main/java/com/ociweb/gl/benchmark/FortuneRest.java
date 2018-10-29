package com.ociweb.gl.benchmark;

import java.util.ArrayList;
import java.util.List;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPRequestReader;
import com.ociweb.gl.api.HTTPResponseService;
import com.ociweb.gl.api.RestListener;
import com.ociweb.gl.api.TickListener;
import com.ociweb.pronghorn.pipe.ObjectPipe;
import com.ociweb.pronghorn.util.AppendableBuilder;
import com.ociweb.pronghorn.util.Appendables;
import com.ociweb.pronghorn.util.template.StringTemplateBuilder;
import com.ociweb.pronghorn.util.template.StringTemplateRenderer;

import io.reactiverse.pgclient.PgIterator;
import io.reactiverse.pgclient.PgPool;
import io.reactiverse.pgclient.Row;

public class FortuneRest implements RestListener, TickListener {

	private HTTPResponseService service;
	private PgPool pool;
	
	//new write must select next free so it knows we are free up to...
	//new read/release must select next data so it knows we have data up to...
	
	//each time we add new one we must notify the output of the new stop position.  -> pub new consume
	//each time we consume one we must notify the input of the new position.     <- pub new release
	//objects remain private at all times. NO because new work msut pick object and pass it over..
	
	
	
	
	
	//SQL results write to these object, these same objects are used by template
	private ObjectPipe<FortunesObject> inFlight;
	
	public FortuneRest(GreenRuntime runtime, PgPool pool, ObjectPipe<FortunesObject> inFlight) {;
	    int maxResponseSize = 1<<18;
	    
		this.pool = pool;	
		this.service = runtime.newCommandChannel().newHTTPResponseService(128, maxResponseSize); 
		this.inFlight = inFlight;
	}

	@Override
	public boolean restRequest(HTTPRequestReader request) {
		
		final FortunesObject target = inFlight.headObject(); //TODO: combined method to TAKE NEXT EMPY...
		if (null!=target) {
			target.setConnectionId(request.getConnectionId());
			target.setSequenceId(request.getSequenceCode());
			target.setStatus(-2);//out for work	
		
			pool.preparedQuery( "SELECT id, message FROM fortune", r -> {
				    //NOTE: we want to do as little work here a s possible since
				    //      we want this thread to get back to work on other calls.
					if (r.succeeded()) {
						target.clear();
						PgIterator resultSet = r.result().iterator();
						
						while (	resultSet.hasNext() ) {
					        Row next = resultSet.next();
							target.addFortune(next.getInteger(0), next.getString(1));						
						}
						target.setStatus(200);
					} else {
						System.out.println("fail: "+r.cause().getLocalizedMessage());
						target.setStatus(500);
					}									
				});
			
			inFlight.moveHeadForward(); //always move to ensure this can be read.  //TODO: remove and combined with above
			return true;
		} else {
			return false;//can not pick up new work now			
		}
		
		
		//return service.publishHTTPResponse(request, 404);
	}

	class DemoObject {

		private final int id;
		private String message;
		
		public DemoObject(int id, String message) {
			this.id = id;
			this.message = message;
		}
		
		public int getId() {
			return id;			
		}

		public String getMessage() {
			return message;
		}
		
	}
	
	StringTemplateRenderer<List<DemoObject>> template =		
			new StringTemplateBuilder<List<DemoObject>>()
			       .add("<!DOCTYPE html> <html> <head><title>Fortunes</title></head> <body> <table> <tr><th>id</th><th>message</th></tr>\n")
			       .add((t,s,i)-> {
						if (i<s.size()) {													
							Appendables.appendHTMLEntityEscaped(
								Appendables.appendValue(t, 
										"<tr><td>", s.get(i).getId(),"</td><td>"), s.get(i).getMessage() ).append("</td></tr>\n");
							return true;
						} else {
							return false;
						}
			         })		
			       .add("</table></body></html>")
			       .finish();
	
	@Override
	public void tickEvent() { //TODO: remove tickEvent here and replace with  pub sub to take next...
		
		//  insert our custom fortune
		List<DemoObject> obj = new ArrayList();
		
		//  sort the response
		
		//  apply template  (note must have escape support)
		
	///	JSONRenderer<T>
		AppendableBuilder target = new AppendableBuilder(1<<21);
		template.render(target, obj);
		
		
	}
	
	
	
	

}
