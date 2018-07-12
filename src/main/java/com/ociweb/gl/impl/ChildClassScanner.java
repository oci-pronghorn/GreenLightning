package com.ociweb.gl.impl;

import com.ociweb.gl.api.MsgRuntime;
import com.ociweb.pronghorn.pipe.ChannelWriter;
import com.ociweb.pronghorn.pipe.util.hash.IntHashTable;
import com.ociweb.pronghorn.util.Appendables;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;

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

	static boolean visitByClass(String topName, Object listener, int depth, 
												 ChildClassScannerVisitor visitor,
												 Class<? extends Object> c,
												 Class targetType, 
												 Object topParent,
												 Collection<Object> seen) {
			
			Field[] fields = c.getDeclaredFields();
	                        
	        int f = fields.length;
	        while (--f >= 0) {
	      
	                fields[f].setAccessible(true);   
	                Object obj = null;
					try {
						obj = fields[f].get(listener);
			
						if (targetType.isInstance(obj)) {
							if (!visitor.visit(obj, topParent, topName)) {
								return false;
							}                              
						} else {   
							String name;
							
							//check for array
							if (obj!=null && obj.getClass().isArray()) {
								
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
							} else
							
							//NOTE: using the TrieParser would be better here.... (faster startup)
							if (    (obj!=null)
									&& (obj.getClass()!=null)
									&&  (!obj.getClass().isPrimitive()) 
									&& (obj != listener) 
									&& (!(name=obj.getClass().getName()).startsWith("java.")) 
									&& (!name.startsWith("[Ljava."))
									&& (!name.startsWith("[["))
									&& (!name.startsWith("[I"))
									&& (!name.startsWith("[B"))
									&& (!name.startsWith("[J"))
									&& (!name.startsWith("[S"))
									
									&& (!name.startsWith("com.ociweb.pronghorn.stage."))
									&& (!name.startsWith("[Lcom.ociweb.pronghorn.stage."))
									
									&& (!name.startsWith("com.ociweb.iot.hardware."))
									&& (!name.startsWith("[Lcom.ociweb.iot.hardware."))
																	
									&& (!name.startsWith("com.ociweb.pronghorn.pipe."))  
									&& (!name.startsWith("[Lcom.ociweb.pronghorn.pipe.")) 
									
									&& (!name.startsWith("com.ociweb.pronghorn.util."))  
									&& (!name.startsWith("[Lcom.ociweb.pronghorn.util.")) 
									
									&& (!name.startsWith("com.ociweb.pronghorn.network."))  
									&& (!name.startsWith("[Lcom.ociweb.pronghorn.network.")) 
									
									&& (!name.startsWith("org.slf4j."))
									&& (!obj.getClass().isEnum())
									&& (!(obj instanceof MsgRuntime))  
									&& (!(obj instanceof ChannelWriter))  
									&& (!(obj instanceof BuilderImpl)) 
									
									&& !fields[f].isSynthetic()
									&& fields[f].isAccessible() 
									&& depth<=11) { //stop recursive depth
								
								if (!seen.contains(obj)) {
									seen.add(obj);
								                		//if (depth <3 && obj.getClass().getName().startsWith("[")) {
								                		//	logger.info(depth+" "+obj.getClass().getName());
								                		//}
								
								//recursive check for command channels
									if (!visitUsedByClass(topName, obj, depth+1, visitor, topParent, targetType, seen)) {
										return false;
									}
								}
							}
						}
					} catch (IllegalArgumentException e) {
						throw new RuntimeException(e);
					} catch (IllegalAccessException e) {
						throw new RuntimeException(e);
					}                                
	   
	        }
	        return true;
		}

	static boolean visitUsedByClass(String name, Object obj, int depth, ChildClassScannerVisitor visitor, 
			     Object topParent, Class targetType, Collection<Object> seen) {
		
	    if (null!=obj) {
		    Class<? extends Object> c = obj.getClass();
		    while (null != c) {
		    	if (!visitByClass(name, obj, depth, visitor, c, targetType, topParent, seen)) {
		    		return false;
		    	}
		    	c = c.getSuperclass();
		    }
	    }
	    return true;
	}

	public static boolean visitUsedByClass(String name, Object listener, ChildClassScannerVisitor visitor, Class target) {
		long start = System.nanoTime();
		boolean result = visitUsedByClass(name, listener, 0, visitor, listener, target, new ArrayList<Object>());
		long duration = System.nanoTime()-start;
		
		Appendables.appendNearestTimeUnit(System.out, duration);
		System.out.println(" duration for scan to find all "+target.getSimpleName()+" instances.");
		
		return result;
	}

}
