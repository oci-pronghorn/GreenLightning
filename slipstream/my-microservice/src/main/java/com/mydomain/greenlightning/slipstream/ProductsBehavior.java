package com.mydomain.greenlightning.slipstream;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPRequestReader;
import com.ociweb.gl.api.HTTPResponseService;
import com.ociweb.gl.api.RestMethodListener;
import com.ociweb.gl.api.StartupListener;
import com.ociweb.json.encode.JSONRenderer;
import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;
import com.ociweb.pronghorn.pipe.StructuredReader;

public class ProductsBehavior implements RestMethodListener, StartupListener {
	
	private final StringBuilder[] names;
	private final long[] quantity;
	private final boolean[] disabled;
	private final HTTPResponseService responseService;
	
	private int selectedId;
	private final JSONRenderer<ProductsBehavior> renderSelected = new JSONRenderer<ProductsBehavior>()
				.startObject()
					.integer("id", o-> o.selectedId )
					.integer("quantity", o-> o.quantity[o.selectedId] )
					.string("name", (o,t)-> t.append(o.names[o.selectedId]) )
					.bool("disabled", o->o.disabled[o.selectedId])
				.endObject();	
	
	public ProductsBehavior(GreenRuntime runtime, int maxProductId) {	
		responseService = runtime.newCommandChannel().newHTTPResponseService(4,256);
		
		names = new StringBuilder[maxProductId+1];
		int i = names.length;
		while (--i>=0) {
			names[i] = new StringBuilder();
		}
		
		quantity = new long[maxProductId+1];		
		disabled = new boolean[maxProductId+1];
	}
		
	public boolean productUpdate(HTTPRequestReader request) {
		StructuredReader structured = request.structured();
		int idx     = structured.readInt(Field.ID);
		
		quantity[idx] = structured.readInt(Field.QUANTITY);
		disabled[idx] = structured.readBoolean(Field.DISABLED);
		names[idx].setLength(0);
		structured.readText(Field.NAME, names[idx] );
		
		return responseService.publishHTTPResponse(request, 200);
		
	}

	
	public boolean productQuery(HTTPRequestReader request) {
		selectedId     = request.structured().readInt(Field.ID);		
		return responseService.publishHTTPResponse(request, 200, HTTPContentTypeDefaults.JSON, w-> renderSelected.render(w, this));
	}
	
	public boolean productAll(HTTPRequestReader request) {
		return responseService.publishHTTPResponse(request, 200, HTTPContentTypeDefaults.JSON, w-> {

			w.append('[');
			boolean isFirst = true;
			for(int i = 0; i<names.length; i++) {
				if (names[i].length()>0) {
					if (!isFirst) {
						w.append(",\n");
					}
					isFirst=false;
					selectedId = i;
					renderSelected.render(w, this);
				}
			}
			w.append(']');
		});
	}

	@Override
	public void startup() {
		//add some default products on startup
		
		quantity[5] = 100;
		disabled[5] = false;
		names[5].append("fork");
		
		quantity[2] = 100;
		disabled[2] = false;	
		names[2].append("spoon");
		
	}

	
}
