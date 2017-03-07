package com.ociweb.gl.impl.stage;

import static com.ociweb.pronghorn.pipe.PipeWriter.publishWrites;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.gl.impl.BuilderImpl;
import com.ociweb.gl.impl.schema.TrafficAckSchema;
import com.ociweb.gl.impl.schema.TrafficReleaseSchema;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeReader;
import com.ociweb.pronghorn.pipe.PipeWriter;
import com.ociweb.pronghorn.stage.PronghornStage;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;
import com.ociweb.pronghorn.util.Blocker;

public abstract class AbstractTrafficOrderedStage extends PronghornStage {

	private final int MAX_DEVICES = 127; //do not know of any hardware yet with more connections than this.

	private final Pipe<TrafficReleaseSchema>[] goPipe;
	private final Pipe<TrafficAckSchema>[] ackPipe;

	protected final BuilderImpl hardware;
	private Blocker connectionBlocker;
		
	protected int[] activeCounts;	
	private int[] activeBlocks;

	private int hitPoints;
    private final GraphManager graphManager;
 
    private int startLoopAt = -1;
    private int mostRecentBlockedConnection = -1;
    
    protected static final long MS_TO_NS = 1_000_000;
    private Number rate;
    private long releaseWindow = 2; //stay here if work will be available in this many ms.
    
	private static final Logger logger = LoggerFactory.getLogger(AbstractTrafficOrderedStage.class);

	//////////////////////////////////////////
	/////////////////////////////////////////
	//Needs testing but this class is complete
	//////////////////////////////////////////
	///do not modify this logic without good reason
	//////////////////////////////////////////

  

	/**
	 * Using real hardware support this stage turns on and off digital pins and sets PWM for analog out.
	 * It supports time based blocks (in ms) specific to each connection.  This way no other commands are
	 * send to that connection until the time expires.  This is across all pipes.
	 * 
	 * 
	 * @param graphManager
	 * @param hardware
	 * @param goPipe
	 * @param ackPipe
	 */
	public AbstractTrafficOrderedStage(GraphManager graphManager, 
			BuilderImpl hardware,
			Pipe<?>[] output,
			Pipe<TrafficReleaseSchema>[] goPipe, Pipe<TrafficAckSchema>[] ackPipe, Pipe<?> ... otherResponse ) {

		super(graphManager, join(goPipe, output), join(ackPipe, otherResponse));

		this.hardware = hardware;
		this.ackPipe = ackPipe;
		this.goPipe = goPipe;
		this.hitPoints = goPipe.length;
		this.graphManager = graphManager;
		

	}

	@Override 
	public void startup() {
	    	       
        //processing can be very time critical so this thread needs to be on of the highest in priority.
        Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
	    
		connectionBlocker = new Blocker(MAX_DEVICES);
		activeCounts = new int[goPipe.length];
		activeBlocks = new int[MAX_DEVICES];
		Arrays.fill(activeCounts, -1); //0 indicates, need to ack, -1 indicates done and ready for more
		Arrays.fill(activeBlocks, -1);
	
		startLoopAt = activeCounts.length;
		
		rate = (Number)graphManager.getNota(graphManager, this.stageId,  GraphManager.SCHEDULE_RATE, null);
	}

	@Override
	public void run() {
		processReleasedCommands(null==rate ?  100_000 : rate.longValue() );		
	}
	
	
	protected void blockChannelDuration(int activePipe, long durationNanos) {
		
		final long durationMills = durationNanos/1_000_000;
		final long remaningNanos = durationNanos%1_000_000;		
			    
	    if (remaningNanos>0) {
	    	final long start = hardware.nanoTime();
	    	final long limit = start+remaningNanos;
	    	while (hardware.nanoTime()<limit) {
	    		Thread.yield();
	    		if (Thread.interrupted()) {
	    			Thread.currentThread().interrupt();
	    			return;
	    		}
	    	}
	    }
	    if (durationMills>0) {
	    	//now pull the current time and wait until ms have passed
	    	hardware.blockChannelUntil(( goPipe[activePipe].id ), hardware.currentTimeMillis() + durationMills );
	    }
	}
	
	protected void blockConnectionDuration(int connection, long durationNanos) {
		
		long durationMills = durationNanos/1_000_000;
		long remaningNanos = durationNanos%1_000_000;		
			    
	    if (remaningNanos>0) {
	    	final long start = hardware.nanoTime();
	    	final long limit = start+remaningNanos;
	    	while (hardware.nanoTime()<limit) {
	    		Thread.yield();
	    		if (Thread.interrupted()) {
	    			Thread.currentThread().interrupt();
	    			return;
	    		}
	    	}
	    }
	    if (durationMills>0) {
	    	//now pull the current time and wait until ms have passed	    	
	    	connectionBlocker.until(connection, hardware.currentTimeMillis() + durationMills );
	    }
	}
	

	protected boolean isConnectionUnBlocked(int connection) {
		return !connectionBlocker.isBlocked(connection);
	}
	

	protected void blockConnectionUntil(int connection, long time) {
		connectionBlocker.until(connection, time);
		mostRecentBlockedConnection = connection;
	}

    protected boolean processReleasedCommands(long timeoutNS) {
   	
        boolean foundWork;
		int[] localActiveCounts = activeCounts;
		long timeLimitNS = timeoutNS + hardware.nanoTime();
        long unblockChannelLimit = -1;
        long windowLimit = 0;
        boolean holdForWindow = false;
		do {
			foundWork = false;
			int a = startLoopAt;
			
				while (--a >= 0) {
				    long now = hardware.currentTimeMillis();
				    				
				    //do not call again if we already know nothing will result from it.
				    if (now>unblockChannelLimit) {
				    	unblockChannelLimit = now+hardware.releaseChannelBlocks(now);
				    }
				    
				    if (isChannelBlocked(a) ) {
				        return true;            
				    }   
				    if (hardware.nanoTime() >= timeLimitNS) {
				        //stop here because we have run out of time, do save our location to start back here.
				        startLoopAt = a+1;
                        return false;
				    }
				    
				    //must clear these before calling processMessages, 
				    //this is the only place we can call release on the connectionBlocker because we
				    //must also enforce which pipe gets the released resource first to ensure atomic operations
				    int releasedConnection = -1;
				    while (-1 != (releasedConnection = connectionBlocker.nextReleased(now, -1))) {   
				    	
				    	int pipeId = activeBlocks[releasedConnection];
				    	if (pipeId>=0) {
				    		a = pipeId;//run from here, its the only one that can continue after the block.
				    		activeBlocks[releasedConnection] = -1;
				    		break;//release the rest on the next iteration since this atomic is preventing further progress
				    	}				    	
					}				    
				    
				    
					//pull all known the values into the active counts array
					if ((localActiveCounts[a] == -1) && PipeReader.tryReadFragment(goPipe[a])) {                    
						readNextCount(a);
					} 
				
					mostRecentBlockedConnection = -1;//clear in case it gets set.
					int startCount = localActiveCounts[a];
					//NOTE: this lock does not prevent other ordered stages from making progress, just this one since it holds the required resource

					//This method must be called at all times to poll I2C
					processMessagesForPipe(a);											

					//send any acks that are outstanding, we did all the work
					if (startCount>0 || 0==localActiveCounts[a]) {
						//there was some work to be done
						if (0==localActiveCounts[a]) {
						    //logger.debug("send ack back to {}",a);					    
						    if (PipeWriter.tryWriteFragment(ackPipe[a], TrafficAckSchema.MSG_DONE_10)) {
								publishWrites(ackPipe[a]);
								localActiveCounts[a] = -1;
								foundWork = true; //keep running may find something else 
							} else {//this will try again later since we did not clear it to -1
								//logger.debug("unable to send ack back to caller, check pipe lenghts. {} {}",a,ackPipe[a]);
							}
						} else {							
							if (localActiveCounts[a]==startCount) {
								//we did none of the work
							} else {		
								foundWork = true;
								//we did some of the work
							    //unable to finish group, try again later, this is critical so that callers can
							    //interact and then block knowing nothing else can get between the commands.
							    
							    //would only happen if
							    // 1. the release has arrived in its pipe before the needed data
							    // 2. this sequence contains a block in the middle (eg a pulse with a non zero time)
							    //    In this second case we have "blocked" all other usages of this connection therefore we only need to ensure
							    //    this pipe (a) is the one to resume when the lock is released.
							    if (mostRecentBlockedConnection>=0) {
							    	//we have a connection we are blocking on.
							    	activeBlocks[mostRecentBlockedConnection] = a;
							    }								
							}							
						}
					}
															
					
				} 
				startLoopAt = activeCounts.length;
			    
				//only stop after we have 1 cycle where no work was done, this ensure all pipes are as empty as possible before releasing the thread.
			    //we also check for 'near' work but only when there is no found work since its more expensive
				long now = hardware.currentTimeMillis();
				if (now>windowLimit) {
					windowLimit = now+releaseWindow;
					holdForWindow = connectionBlocker.willReleaseInWindow(windowLimit);
				}
				
		} while (foundWork | holdForWindow);
		return true;
    }

    protected boolean isChannelBlocked(int a) {
        return hardware.isChannelBlocked( goPipe[a].id );
    }
    
    protected boolean isChannelUnBlocked(int a) {
        return !hardware.isChannelBlocked( goPipe[a].id );
    }

	protected abstract void processMessagesForPipe(int a);

	private void readNextCount(final int a) {
		Pipe<TrafficReleaseSchema> localPipe = goPipe[a];
		assert(PipeReader.isNewMessage(localPipe)) : "This test should only have one simple message made up of one fragment";
		
		int msgIdx = PipeReader.getMsgIdx(localPipe);
		if(TrafficReleaseSchema.MSG_RELEASE_20 == msgIdx){
			assert(-1==activeCounts[a]);
			activeCounts[a] = PipeReader.readInt(localPipe, TrafficReleaseSchema.MSG_RELEASE_20_FIELD_COUNT_22);
		}else{
			System.err.println("shtudown ack");
			assert(msgIdx == -1);
			if (--hitPoints == 0) {
				requestShutdown();
			}
		}
		PipeReader.releaseReadLock(localPipe);

	}

	protected void decReleaseCount(int a) {
		//logger.info("decrease the count for {} down from {} ",a,activeCounts[a]);
		activeCounts[a]--;
		
	}

	protected boolean hasReleaseCountRemaining(int a) {
		return (activeCounts.length>0)&&(activeCounts[a] > 0);
	}

}