package com.ociweb.gl.test;

import java.io.IOException;

import com.ociweb.pronghorn.stage.scheduling.ElapsedTimeRecorder;
import com.ociweb.pronghorn.util.Appendables;

public class DefaultParallelClientLoadTesterOutput implements ParallelClientLoadTesterOutput {
  	
	private boolean recordEveryDisconnect = false;
  	
	private final Appendable target;
	public DefaultParallelClientLoadTesterOutput(Appendable target) {
		this.target = target;
	}
	
    @Override
    public void progress(int percentDone, long sumTimeouts, long sumInvalid) {
    	
        try {
        	Appendables.appendValue(target, percentDone);
			target.append("% complete,  ");
			
			if (sumTimeouts>0) {
				Appendables.appendValue(target, sumTimeouts);
				target.append(" retried,  ");
			}
			
			if (sumInvalid>0) {
				Appendables.appendValue(target, sumInvalid);
				target.append(" invalid,   epoch:");
			}
			
			Appendables.appendEpochTime(target, System.currentTimeMillis());
			target.append("\n");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
    }

    @Override
    public void timout(long responseTimeoutNS) {
    	try {
    		Appendables.appendNearestTimeUnit(target.append("Failed response detected after timeout of: "), responseTimeoutNS).append('\n');
    	} catch (IOException e) {
			throw new RuntimeException(e);
		}
    }

    @Override
    public void end(
            ElapsedTimeRecorder etr, long testDuration, long totalMessages, long totalTimeSumNS, long serverCallsPerSecond,
            long sendAttempts, long sendFailures, long timeouts, long responsesReceived, long invalidResponses) {
        try {
            Thread.sleep(100); //fixing system out IS broken problem.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        try {
	        Appendables.appendNearestTimeUnit(target, testDuration).append(" test duration\n");
	        Appendables.appendValue(target, serverCallsPerSecond).append(" total calls per second against server\n");
	
	        target.append("\n");
	        etr.report(target).append("\n");
	             
	        if (totalMessages>0) {
	        	long avgLatencyNS = totalTimeSumNS/(long)totalMessages;
	        	Appendables.appendNearestTimeUnit(target, avgLatencyNS).append(" average\n");
	        } else {
	        	target.append("warning: zero messages tested\n");
	        }
	
	        target.append("Total messages: " + totalMessages+"\n");
	        target.append("Send failures: " + sendFailures + " out of " + sendAttempts+"\n");
	        target.append("Timeouts: " + timeouts+"\n");
	        target.append("Responses invalid: " + invalidResponses + " out of " + responsesReceived+"\n");
	        target.append("\n");
	    } catch (IOException e) {
			throw new RuntimeException(e);
		}
    }

    @Override
    public void finishedWarmup() {
    	try {
    		target.append("---------------- finished warmup -------------\n");
    	} catch (IOException e) {
			throw new RuntimeException(e);
		}
    }

    @Override
    public void longCallDetected(int track, long duration, long now, long start) {
 
    	Appendables.appendEpochTime(
                Appendables.appendEpochTime(
                        Appendables.appendValue(
                                Appendables.appendNearestTimeUnit(System.err, duration)
                                        .append(" long call detected for track:")
                                ,(track)).append(" happend in window :")
                        ,start).append(" - ")
                ,now).append("\n");
        
        
    }

    @Override
    public void connectionClosed(int track) {
  
    	if (recordEveryDisconnect) {
	    	try {
	    		target.append("Connection Closed on track: " + track + "\n");
	    	} catch (IOException e) {
				throw new RuntimeException(e);
			}
    	}
    }

    @Override
    public void failedToStart(int inFlight) {
        System.err.println("Unable to send "+inFlight+" messages to start up.");
    }
}
