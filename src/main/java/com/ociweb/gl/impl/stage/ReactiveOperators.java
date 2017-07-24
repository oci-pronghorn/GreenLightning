package com.ociweb.gl.impl.stage;

import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.gl.api.Behavior;
import com.ociweb.pronghorn.pipe.MessageSchema;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeConfigManager;

public class ReactiveOperators {

	private final static Logger logger = LoggerFactory.getLogger(ReactiveOperators.class);
		
	public final ArrayList<Class<?>> interfaces = new ArrayList<Class<?>>();
	public final ArrayList<ReactiveOperator> operators = new ArrayList<ReactiveOperator>();
	public final ArrayList<MessageSchema> schemas = new ArrayList<MessageSchema>();
		
	public ReactiveOperators addOperator(Class<?> objectClazz, MessageSchema<?> schema, ReactiveOperator operator) {
		
		interfaces.add(objectClazz);
		operators.add(operator);
		schemas.add(schema);
				
		return this;
	}
	
	public ReactiveOperator getOperator(Pipe p) {
		int i = schemas.size();
		while (--i>=0) {
			if (Pipe.isForSchema(p, schemas.get(i))) {
				return operators.get(i);
			}
		}
		throw new UnsupportedOperationException("can not find operator for pipe "+p);
	}

	//creates inputs for this Transducer or Behavior
	public Pipe[] createPipes(Object listener, PipeConfigManager pcm) {
		return createPipes(0, 0, listener, pcm);
	}

	private Pipe[] createPipes(int i, int matches, Object listener, PipeConfigManager pcm) {
		 if (i<interfaces.size()) {
			 boolean doesMatch = interfaces.get(i).isInstance(listener);
			 Pipe[] result = createPipes(i+1,
					                     doesMatch ? 1+matches : matches,
					                     listener,
					                     pcm);
			 if (doesMatch) {
				 result[matches] = pcm.newPipe(schemas.get(i).getClass());
			 }
			 return result;
		 } else {
			 return new Pipe[matches];
		 }
	}
	
}
