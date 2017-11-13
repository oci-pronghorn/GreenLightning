package com.ociweb.gl.impl.stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.gl.api.MsgRuntime;
import com.ociweb.gl.impl.BuilderImpl;
import com.ociweb.gl.impl.schema.TrafficAckSchema;
import com.ociweb.gl.impl.schema.TrafficOrderSchema;
import com.ociweb.gl.impl.schema.TrafficReleaseSchema;
import com.ociweb.pronghorn.pipe.FragmentWriter;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.stage.PronghornStage;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;

/**
 * Supports a single primary input pipe that defines which  output pipes should be processed and in which order.
 * 
 * @author Nathan Tippy
 *
 */
public class TrafficCopStage extends PronghornStage {
    
    private Pipe<TrafficOrderSchema> primaryIn; 
    private Pipe<TrafficAckSchema>[] ackIn;
    private Pipe<TrafficReleaseSchema>[] goOut;
    private final static Logger logger = LoggerFactory.getLogger(TrafficCopStage.class);
    
    private int ackExpectedOn = -1;   
    private GraphManager graphManager;
    private final long msAckTimeout;
    private long ackExpectedTime;
    
    private int goPendingOnPipe = -1;
    private int goPendingOnPipeCount = 0;
    private MsgRuntime<?,?> runtime;
    private BuilderImpl builder;
    private boolean shutdownInProgress;
    
    public TrafficCopStage(GraphManager graphManager, long msAckTimeout, 
    		               Pipe<TrafficOrderSchema> primaryIn, 
    		               Pipe<TrafficAckSchema>[] ackIn,  
    		               Pipe<TrafficReleaseSchema>[] goOut, 
    		               MsgRuntime<?,?> runtime,
    		               BuilderImpl builder) {
    	super(graphManager, join(ackIn, primaryIn), goOut);
    	
    	assert(ackIn.length == goOut.length);
    	this.msAckTimeout = msAckTimeout;
        this.primaryIn = primaryIn;
        this.ackIn = ackIn;
        this.goOut = goOut;
      
        
        this.graphManager = graphManager;//for toString
        this.builder = builder;
        this.runtime = runtime;
        
        //force all commands to happen upon publish and release
        this.supportsBatchedPublish = false;
        this.supportsBatchedRelease = false;

        GraphManager.addNota(graphManager, GraphManager.DOT_BACKGROUND, "cadetblue2", this);
        GraphManager.addNota(graphManager, GraphManager.TRIGGER, GraphManager.TRIGGER, this);
        
    }    
    
    public String toString() {
        String result = super.toString();
        
        final int localActEO = ackExpectedOn;//must hold since it can change while using
        boolean needsAck = (localActEO>=0)&&(localActEO<ackIn.length)&&(null!=ackIn[localActEO]);
		return result+ ( needsAck ? 
        		" AckExpectedOn:"+localActEO+" "+GraphManager.getRingProducer(graphManager, ackIn[localActEO].id)
        		: "" );
    }

    @Override
    public void run() {

    	if (shutdownInProgress) {

    		int i = goOut.length;
    		while (--i>=0) {
    			if ((null!=goOut[i]) && (!Pipe.hasRoomForWrite(goOut[i]))) {
    				return;
    			}
    		}
    		//all outgoing pipes have room
    		Pipe.publishEOF(goOut);
    		requestShutdown();
    		
    		////////////////////
    		//Traffic cops can be responsible for shutting down the system
			//Upon getting -1 this will trigger the rest of the shutdown
    		runtime.shutdownRuntime();
			///////////////////
			
    		
    		return;
    	}
    	
    	int maxIterations = 100;
        do {
            ////////////////////////////////////////////////
            //check first if we are waiting for an ack back
            ////////////////////////////////////////////////
            if (ackExpectedOn>=0 && null!=ackIn[ackExpectedOn]) {                              
                
                if (!Pipe.hasContentToRead(ackIn[ackExpectedOn])) {
                    
                    if (System.currentTimeMillis() > ackExpectedTime) {
                    	shutdownInProgress = true;
                    	logger.info(" *** Expected to get ack back from "+GraphManager.getRingProducer(graphManager, +ackIn[ackExpectedOn].id)+" within "+msAckTimeout+"ms \nExpected ack on pipe:"+ackIn[ackExpectedOn]);
                    
                    }
                    
                    //must watch for shutdown signal while we are also watching for next ack.
                    detectShutdownInProgress();
                    
                    return;//we are still waiting for requested operation to complete
                } else {
                	
                	Pipe.skipNextFragment(ackIn[ackExpectedOn]);
                	
                    ackExpectedOn = -1;//clear value we are no longer waiting
                }
            }
            
            ////////////////////////////////////////////////////////
            //check second to roll up release messages for new stages from primaryIn
            ////////////////////////////////////////////////////////
            
            if (-1==goPendingOnPipe) {
            	if ( (!Pipe.hasContentToRead(primaryIn)) ||
            		 builder.isChannelBlocked (primaryIn.id )
            	   ) {
            		return;//there is nothing todo
            	} else {       
            		
            		int msgIdx = Pipe.takeMsgIdx(primaryIn);

            		if (TrafficOrderSchema.MSG_GO_10 == msgIdx) {
            			
            			goPendingOnPipe = ackExpectedOn = Pipe.takeInt(primaryIn);
            			assert(goPendingOnPipe < goOut.length) : "Go command is out of bounds "+goPendingOnPipe+" vs "+goOut.length;
            			assert(goPendingOnPipe >= 0) : "Go command pipe must be positive";
            			
            			goPendingOnPipeCount = Pipe.takeInt(primaryIn);
            			//NOTE: since we might not send release right  away and we have released the input pipe the outgoing release
            			//      may be dropped upon clean shutdown.  This is OK since dropping work is what we want during a shutdown request.
            			
            			Pipe.confirmLowLevelRead(primaryIn, Pipe.sizeOf(primaryIn, TrafficOrderSchema.MSG_GO_10));
            			Pipe.releaseReadLock(primaryIn);
            			
            			ackExpectedTime = msAckTimeout>0 ? msAckTimeout+System.currentTimeMillis() : Long.MAX_VALUE; 
            			
            		} else if (TrafficOrderSchema.MSG_BLOCKCHANNEL_22 == msgIdx) {	
            	
            			builder.blockChannelDuration( Pipe.takeLong(primaryIn), primaryIn.id);
            			
            			Pipe.confirmLowLevelRead(primaryIn, Pipe.sizeOf(primaryIn, TrafficOrderSchema.MSG_BLOCKCHANNEL_22));
            			Pipe.releaseReadLock(primaryIn); 
            			
            		} else if (TrafficOrderSchema.MSG_BLOCKCHANNELUNTIL_23 == msgIdx) {	
            			
            			long fieldTimeMS = Pipe.takeLong(primaryIn);
            			
            			builder.blockChannelUntil(primaryIn.id, fieldTimeMS);
            			
            			Pipe.confirmLowLevelRead(primaryIn, Pipe.sizeOf(primaryIn, TrafficOrderSchema.MSG_BLOCKCHANNELUNTIL_23));
            			Pipe.releaseReadLock(primaryIn); 
            		} else {
        				
            			//this may be shutting down or an unsupported message
            			int endMsg =  msgIdx;
            			assert(-1 == endMsg) : "Expected end of stream however got unsupported message: "+endMsg;
            			shutdownInProgress = true;
            			
            			Pipe.confirmLowLevelRead(primaryIn, Pipe.EOF_SIZE);
            			Pipe.releaseReadLock(primaryIn); 
            			            			
            			return;//reached end of stream
            		}
            	}            	
            }
            
            //only done when we are managing another stage
            if (goPendingOnPipe != -1) {

	            //check if following messages can be merged to the current release message, if its a release for the same pipe as the current active
	            if (	Pipe.peekMsg(primaryIn, TrafficOrderSchema.MSG_GO_10)
	            		&& (Pipe.peekInt(primaryIn, TrafficOrderSchema.MSG_GO_10_FIELD_PIPEIDX_11) == goPendingOnPipe) 
	            		&& Pipe.hasContentToRead(primaryIn)) {
	            	
	            	final int msgIdx = Pipe.takeMsgIdx(primaryIn);
	            	if (msgIdx==TrafficOrderSchema.MSG_GO_10) {
	
	            		final int pipeIdx = Pipe.takeInt(primaryIn);
	            		final int count = Pipe.takeInt(primaryIn);
	            		
	            		assert(pipeIdx == goPendingOnPipe);
	            		goPendingOnPipeCount += count;
	            		
	          			Pipe.confirmLowLevelRead(primaryIn, Pipe.sizeOf(primaryIn, TrafficOrderSchema.MSG_GO_10));
            			Pipe.releaseReadLock(primaryIn);
	            		
	            	} else {
	        			assert(-1 == msgIdx) : "Expected end of stream however got unsupported message: "+msgIdx;
	        			shutdownInProgress = true;
	        				        			
            			Pipe.confirmLowLevelRead(primaryIn, Pipe.EOF_SIZE);
            			Pipe.releaseReadLock(primaryIn); 
            			 
	        			return;//reached end of stream
	            	}
	            }
	            
	            /////////////////////////////////////////////////////////
	            //check third for room to send the pending go release message
	            /////////////////////////////////////////////////////////            
	        	Pipe<TrafficReleaseSchema> releasePipe = goOut[goPendingOnPipe];
	        	//can be null for event types which are not used in this particular runtime
	            if (null!=releasePipe 
	            	&& Pipe.hasRoomForWrite(releasePipe)) { 
	            	
	            	FragmentWriter.writeI(releasePipe, TrafficReleaseSchema.MSG_RELEASE_20, goPendingOnPipeCount);

	            	goPendingOnPipe = -1;
	            } else {
	            	 //System.err.println("exit 5");
	            	return;//try again later
	            }            

            }
            
        } while(--maxIterations>=0);
        //System.err.println("exit 6");
    }

	private void detectShutdownInProgress() {
		if (Pipe.peekMsg(primaryIn, -1)) {
			//shutdown in progress so give up waiting
			Pipe.takeMsgIdx(primaryIn);
			Pipe.confirmLowLevelRead(primaryIn, Pipe.EOF_SIZE);
			Pipe.releaseReadLock(primaryIn);                    	
			shutdownInProgress = true;
		} 
	}

}
