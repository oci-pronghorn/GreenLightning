package com.ociweb.gl.impl.stage;

import static com.ociweb.pronghorn.pipe.PipeWriter.publishWrites;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.gl.api.MsgRuntime;
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

	private final Pipe<?>[] etcAndDataPipe;
	private final Pipe<TrafficReleaseSchema>[] goPipe;
	private final Pipe<TrafficAckSchema>[] ackPipe;

	public final BuilderImpl hardware;
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
    
    private final MsgRuntime<?,?> runtime;
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
	 */
	public AbstractTrafficOrderedStage(GraphManager graphManager, 
			MsgRuntime<?,?> runtime,
			BuilderImpl hardware,
			Pipe<?>[] output,
			Pipe<TrafficReleaseSchema>[] goPipe,
			Pipe<TrafficAckSchema>[] ackPipe,
			
			Pipe<?> ... otherResponse ) {

		super(graphManager, join(goPipe, output), join(ackPipe, otherResponse));
	       
		assert(output.length >= goPipe.length);
		
		logger.info("Warning, 2ms latency may be introduced due to longer timeout on traffic stages. {}",this.getClass().getSimpleName());
		
		this.runtime = runtime;
		this.hardware = hardware;
		this.etcAndDataPipe = output;//the last few pipes align with goPipe
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
	
	
	protected void blockConnectionDuration(int connection, long durationNanos) {
		
		long durationMills = durationNanos/1_000_000;
		long remaningNanos = durationNanos%1_000_000;		
			    
	    if (remaningNanos>0) {
	    	try {
	    		long limit = System.nanoTime()+remaningNanos;
				Thread.sleep(0L, (int)remaningNanos);
				long dif;
				while ((dif = (limit-System.nanoTime()))>0) {
					if (dif>100) {
						Thread.yield();
					}
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
    			return;
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
		
		long now = hardware.currentTimeMillis();
		//TODO: update this so its not limited to the nearest MS, we need NS time.
		long timeLimit = (int) (1+(timeoutNS/1_000_000)) + now; //rounds timeout up to next MS 
      
		long unblockChannelLimit = -1;
        long windowLimit = 0;
        boolean holdForWindow = false;
		do {
			foundWork = false;
			int a = startLoopAt;
			
				while (--a >= 0) {
				    				    				
				    //do not call again if we already know nothing will result from it.
				    if (now>unblockChannelLimit) {
				    	unblockChannelLimit = now+hardware.releaseChannelBlocks(now);
				    }
				    
				    if (isChannelBlocked(a) ) {
				        return true;            
				    }   
				    if (now >= timeLimit) {
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
				    
				    //TODO: this high level tryReadFragment is a hot spot.
				    //      The traffic classes should be changed to low level usages.
				    
					//pull all known the values into the active counts array
					if (localActiveCounts[a] == -1 ) {                    
						readNextCount(a);
					} 
				
					mostRecentBlockedConnection = -1;//clear in case it gets set.
					int startCount = localActiveCounts[a];
					//NOTE: this lock does not prevent other ordered stages from making progress, just this one since it holds the required resource

					//This method must be called at all times to poll as needed.
					processMessagesForPipe(a);											

					//send any acks that are outstanding, we did all the work
					if (startCount>0 || 0==localActiveCounts[a]) {
						//there was some work to be done
						if (0==localActiveCounts[a]) {
						    //logger.debug("send ack back to {}",a);
							Pipe<TrafficAckSchema> pipe = ackPipe[a];
							
							if (null != pipe) {
							    if (PipeWriter.tryWriteFragment(pipe, TrafficAckSchema.MSG_DONE_10)) {
									publishWrites(pipe);
									localActiveCounts[a] = -1;
									foundWork = true; //keep running may find something else 
								}
							} else {
								localActiveCounts[a] = -1;
								foundWork = true; //keep running may find something else 
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
					now = hardware.currentTimeMillis();									
					
				} 
				startLoopAt = activeCounts.length;
			    
				//only stop after we have 1 cycle where no work was done, this ensure all pipes are as empty as possible before releasing the thread.
			    //we also check for 'near' work but only when there is no found work since its more expensive
				if (now>windowLimit) {
					windowLimit = now+releaseWindow;
					holdForWindow = connectionBlocker.willReleaseInWindow(windowLimit);
				}
				
		} while (foundWork | holdForWindow);
		return true;
    }

    protected boolean isChannelBlocked(int a) {    	
        return (null!=goPipe[a]) && hardware.isChannelBlocked( goPipe[a].id );
    }
    
    protected boolean isChannelUnBlocked(int a) {
        return (null==goPipe[a]) || (!hardware.isChannelBlocked( goPipe[a].id ));
    }

    protected int goPipeId(int a) {
    	return goPipe[a].id;
    }
    
	protected abstract void processMessagesForPipe(int a);

	private void readNextCount(final int a) {
		Pipe<TrafficReleaseSchema> localPipe = goPipe[a];
		if (null != localPipe) {			
			countFromGoPipe(a, localPipe);
		} else {
			//local pipe is null so we must review the input
			//if there is data we mark it as 1 to be done.
			noGoDoSingles(a, etcAndDataPipe[a+etcAndDataPipe.length-goPipe.length]);
		}
	}

	//TOOD: convert to Low level, this is a hot spot.
	private void countFromGoPipe(final int a, Pipe<TrafficReleaseSchema> localPipe) {
		if (PipeReader.tryReadFragment(localPipe)) { 
		
			assert(PipeReader.isNewMessage(localPipe)) : "This test should only have one simple message made up of one fragment";
			
			int msgIdx = PipeReader.getMsgIdx(localPipe);
			if(TrafficReleaseSchema.MSG_RELEASE_20 == msgIdx){
				assert(-1==activeCounts[a]);
				activeCounts[a] = PipeReader.readInt(localPipe, TrafficReleaseSchema.MSG_RELEASE_20_FIELD_COUNT_22);
			} else {
				assert(msgIdx == -1);
				if (--hitPoints == 0) {
					requestShutdown();
				}
			}
			PipeReader.releaseReadLock(localPipe);
		}
	}

	private void noGoDoSingles(final int a, Pipe<?> pipe) {
		if (Pipe.isEmpty(pipe)) {
		} else {
			noGoProcessSingleMessage(a, pipe);
		}
	}

	private void noGoProcessSingleMessage(final int a, Pipe<?> pipe) {
		//detect any request to begin the shutdown process
		if (!PipeReader.peekMsg(pipe, -1)) {					
			activeCounts[a] = 1; //NOTE: only do 1 at a time
		} else {				
			if (PipeReader.tryReadFragment(pipe)) {
				PipeReader.releaseReadLock(pipe);
			}				
			runtime.shutdownRuntime();
		}
	}

	protected void decReleaseCount(int a) {
		//logger.info("decrease the count for {} down from {} ",a,activeCounts[a]);
		activeCounts[a]--;
		
	}

	protected boolean hasReleaseCountRemaining(int a) {
		return (activeCounts.length>0)&&(activeCounts[a] > 0);
	}

}