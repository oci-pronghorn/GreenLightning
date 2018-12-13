package com.ociweb.gl.impl.stage;

import com.ociweb.gl.api.Grouper;
import com.ociweb.gl.impl.BuilderImpl;
import com.ociweb.pronghorn.pipe.MessageSchema;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

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

	/**
	 *
	 * @param p Pipe arg used for Pipe.isForSchema
	 * @param doNotThrow boolean if the pipe does not match return null instead of throwing
	 * @return operators.get(i) if (Pipe.isForSchema(p, schemas.get(i)))
	 */
	public ReactiveOperator getOperator(Pipe p, boolean doNotThrow) {
		
		int i = schemas.size();
		while (--i>=0) {
			if (Pipe.isForSchema(p, schemas.get(i))) {
				return operators.get(i);
			}
		}
		
		StringBuilder text = new StringBuilder();
		i = schemas.size();
		while (--i>=0) {
			text.append(schemas.get(i).getClass().getSimpleName()).append(",");
		}
		if (doNotThrow) {
			return null;
		} else {
			throw new UnsupportedOperationException("can not find operator by schema for pipe "+p+"\n in schemas "+text);
		}
	}

	/**
	 * A method that creates inputs for this Transducer or Behavior
	 * @param builder BuilderImpl arg used for createPipes
	 * @param listener Object arg used for createPipes
	 * @param g Grouper arg used for createPipes
	 * @return Pipe created by implementing createPipes(builder, 0, 0, listener, g)
	 */
	public Pipe[] createPipes(BuilderImpl builder, Object listener, Grouper g) {
		return createPipes(builder, 0, 0, listener, g);
	}

	private Pipe[] createPipes(BuilderImpl builder, int i, int matches, Object listener, Grouper g) {
		 if (i<interfaces.size()) {
			 
			 final PipeConfig config = g.config(builder.schemaMapper(schemas.get(i)));
			 final boolean isInUse = null!=config;
			 final boolean doesMatch = interfaces.get(i).isInstance(listener) && isInUse;

			 Pipe[] result = createPipes(builder, i+1,
					                     doesMatch ? 1+matches : matches,
					                     listener, g);
			 if (doesMatch) {
				// logger.info("Does Match! {}", listener);
				result[matches] = new Pipe(config.grow2x());
			 }
			 return result;
		 } else {
			 return new Pipe[matches];
		 }
	}
	
}
