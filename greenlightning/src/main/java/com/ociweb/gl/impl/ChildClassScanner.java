package com.ociweb.gl.impl;

import java.io.Externalizable;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.gl.api.MsgRuntime;
import com.ociweb.pronghorn.pipe.ChannelWriter;
import com.ociweb.pronghorn.pipe.util.hash.IntHashTable;
import com.ociweb.pronghorn.util.Appendables;
import com.ociweb.pronghorn.util.TrieParser;
import com.ociweb.pronghorn.util.TrieParserReaderLocal;

/**
 * Scans up the object tree to find instances of specific classes
 * @author Nathan Tippy
 *
 */
public class ChildClassScanner {

	private static final Logger logger = LoggerFactory.getLogger(ChildClassScanner.class);

	/**
	 *
	 * @param child Object arg used for System.identityHashCode
	 * @param topParent Object arg used for System.identityHashCode
	 * @param usageChecker IntHashTable arg compared against parentHash
	 * @return if IntHashTable.hasItem and parentHash!=IntHashTable return false else true
	 */
	public static boolean notPreviouslyHeld(Object child, 
			                                   Object topParent, 
			                                   IntHashTable usageChecker) {
	    int hash = System.identityHashCode(child);
	    int parentHash = System.identityHashCode(topParent);           
	    if (IntHashTable.hasItem(usageChecker, hash)) {
	    	if (parentHash!=IntHashTable.getItem(usageChecker, hash)) {
	    		return false;
	    	}
	    } 
	    //keep so this is detected later if use
	    IntHashTable.setItem(usageChecker, hash, parentHash);
	    return true;
	}
	///////////
	///////////

	static TrieParser textToBlock  = buildBlockingTypes();
	
	
	static boolean visitByClass(String topName, Object listener, int depth, 
												 ChildClassScannerVisitor visitor,
												 Class<? extends Object> c,
												 Class targetType, 
												 Object topParent,
												 Collection<Object> seen) {
			
		
		
			Field[] fields = c.getDeclaredFields();
	                        
	        int f = fields.length;
	        
	        while (--f >= 0) {
	        		final Field field = fields[f];
	        	
	                field.setAccessible(true);   
	                Object obj = null;
					try {
						if (Modifier.isTransient(field.getModifiers())) {
							continue;//skip over all transient fields
						};						
						
						obj = field.get(listener);
						if (targetType.isInstance(obj)) {
							if (!visitor.visit(obj, topParent, topName)) {
								return false;
							}							
						} else {   
							String name;
							
							//check for array
							if (obj!=null && obj.getClass().isArray()) {
								
								Class<?> cType = obj.getClass().getComponentType();
								if ((!cType.isPrimitive()) 
									&& !cType.isSynthetic()									
								    && (!CharSequence.class.isAssignableFrom(cType))
								    && (!String.class.isAssignableFrom(cType))
								    && (!Externalizable.class.isAssignableFrom(cType))								    
								    
								    && (!(name=cType.getName()).startsWith("java.")) 
								    
								    && (-1 == TrieParserReaderLocal.get().query(textToBlock, name))
								    																				
										) {
										
									
									int i = Array.getLength(obj);
								    while(--i>=0) {
								        Object arrayElement = Array.get(obj, i);
								        
								        if (!seen.contains(arrayElement)) {
											seen.add(arrayElement);
										    //recursive check for command channels
											if (!visitUsedByClass(topName, arrayElement, depth+1, visitor, topParent, targetType, seen)) {
												return false;
											}
										}
								    }
								}
							} else
							
							//NOTE: using the TrieParser would be better here.... (faster startup)
							if (    (obj!=null)
									&& (obj.getClass()!=null)
									&&  (!obj.getClass().isPrimitive()) 
									&& (obj != listener) 
									&& (!(name=obj.getClass().getName()).startsWith("java.")) 
									
									&& (-1 == TrieParserReaderLocal.get().query(textToBlock, name))
									&& (!name.endsWith("Logger"))
									&& (!name.endsWith("Factory"))
									&& (!name.endsWith("Executor"))
									
									&& (!obj.getClass().isEnum())
									&& (!(obj instanceof MsgRuntime))  
									&& (!(obj instanceof ChannelWriter))  
									&& (!(obj instanceof BuilderImpl))
									&& (!(obj instanceof Externalizable))									
									
									&& !fields[f].isSynthetic()
									&& fields[f].isAccessible() 
									&& depth<=8) { //stop recursive depth at this point...
								
								if (!seen.contains(obj)) {
									seen.add(obj);
									
									//if (depth<=2) {
									//	System.out.println("     "+depth+"  "+obj.getClass().getName()+"  "+name+"  "+topParent.getClass().getName());
									//}            	
									//if (depth >4 ) {
								                		//	logger.info(depth+" "+obj.getClass().getName());
								                		//}
								
								    //recursive check for command channels
									if (!visitUsedByClass(topName, obj, depth+1, visitor, topParent, targetType, seen)) {
										return false;
									}
								}
							}
						}
					} catch (Exception e) {
						//do not stop on exception, must continue scan
						if (e instanceof UnsupportedOperationException) {							
						} else {
							logger.trace("unxpected error while scanning", e);							
						}
					}                             
	   
	        }
	        return true;
		}

	private static TrieParser buildBlockingTypes() {
		TrieParser map = new TrieParser(400, 1, false, false, false);
		
		map.setUTF8Value("[Ljava.",1);
        map.setUTF8Value("[[",1);
		map.setUTF8Value("[I",1);
		map.setUTF8Value("[B",1);
		map.setUTF8Value("[J",1);
		map.setUTF8Value("[S",1);
				
		map.setUTF8Value("io.reactiverse.pgclient.impl.",1); 
		map.setUTF8Value("[Lio.reactiverse.pgclient.impl.",1);
		
		map.setUTF8Value("sun.",1); 
		map.setUTF8Value("[Lsun.",1);
		
		map.setUTF8Value("com.ociweb.pronghorn.stage.",1);
		map.setUTF8Value("[Lcom.ociweb.pronghorn.stage.",1);
				
		map.setUTF8Value("com.ociweb.iot.hardware.",1);
		map.setUTF8Value("[Lcom.ociweb.iot.hardware.",1);
												
		map.setUTF8Value("com.ociweb.pronghorn.pipe.",1); 
		map.setUTF8Value("[Lcom.ociweb.pronghorn.pipe.",1);
				
		map.setUTF8Value("com.ociweb.pronghorn.util.",1); 
		map.setUTF8Value("[Lcom.ociweb.pronghorn.util.",1);
				
		map.setUTF8Value("com.ociweb.pronghorn.network.",1);  
		map.setUTF8Value("[Lcom.ociweb.pronghorn.network.",1);
													
		map.setUTF8Value("com.ociweb.gl.impl.",1); 
		map.setUTF8Value("[Lcom.ociweb.gl.impl.",1);
		
		map.setUTF8Value("com.ociweb.gl.test.",1); 
		map.setUTF8Value("[Lcom.ociweb.gl.test.",1);
		
		map.setUTF8Value("com.ociweb.json.encode.JSONRenderer",1);		
		
		map.setUTF8Value("org.slf4j.",1);
		
		return map;
	}

	static boolean visitUsedByClass(String name, Object obj, int depth, ChildClassScannerVisitor visitor, 
			     Object topParent, Class targetType, Collection<Object> seen) {
		
	    if (null!=obj) {
		    Class<? extends Object> c = obj.getClass();
		    while ((null!=c) && (c!=Object.class)) {
		    	try {
			    	if (!visitByClass(name, obj, depth, visitor, c, targetType, topParent, seen)) {
			    		return false;
			    	}
		    	} catch (Exception e) {
		    		return false;
		    	}
		    	c = c.getSuperclass();
		    }
	    }
	    return true;
	}

	public static boolean visitUsedByClass(String name, Object listener, ChildClassScannerVisitor visitor, Class target) {

				
		long start = System.nanoTime();
		boolean result = visitUsedByClass(name, listener, 0, visitor, listener, target, new HashSet<Object>());
		long duration = System.nanoTime()-start;
		
		//just report the longest durations
		if (duration > 200_000_000L) {
			StringBuilder temp = new StringBuilder();//must create this just to show this rare error as a single block.
			Appendables.appendNearestTimeUnit(temp, duration);
			temp.append(" duration for scan to find all ");
			temp.append(target.getSimpleName());
			temp.append(" instances inside ");
			temp.append(listener.getClass().getSimpleName());
			System.out.println(temp);
		}
		return result;
	}

}
