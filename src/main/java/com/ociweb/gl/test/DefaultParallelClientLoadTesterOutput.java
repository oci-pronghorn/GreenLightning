package com.ociweb.gl.test;

import com.ociweb.pronghorn.stage.scheduling.ElapsedTimeRecorder;
import com.ociweb.pronghorn.util.Appendables;

public class DefaultParallelClientLoadTesterOutput implements ParallelClientLoadTesterOutput {

    @Override
    public void progress(int pctDone, int sumFail) {
        Appendables.appendValue(
                Appendables.appendValue(System.out, pctDone)
                        .append("% complete  "), sumFail).append(" failed\n");
    }

    @Override
    public void end(ElapsedTimeRecorder etr, int totalMessages, long totalTimeSumNS, int failedMessagesSum, long duration, long serverCallsPerSecond) {
        try {
            Thread.sleep(100); //fixing system out IS broken problem.
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Appendables.appendNearestTimeUnit(System.out, duration).append(" test duration\n");
        Appendables.appendValue(System.out, serverCallsPerSecond).append(" total calls per second against server\n");

        System.out.println();
        etr.report(System.out).append("\n");
        System.out.println();
        
        if (totalTimeSumNS>0) {
        	long requestsPerSecond =  (1_000_000_000L*(long)totalMessages)/totalTimeSumNS;
        	System.out.println(requestsPerSecond+" requests per second per tack");
        } else {
        	System.out.println("warning: zero total time");
        }
        
        if (totalMessages>0) {
        	long avgLatencyNS = totalTimeSumNS/(long)totalMessages;
        	Appendables.appendNearestTimeUnit(System.out, avgLatencyNS).append(" average\n");
        } else {
        	System.out.println("warning: zero messages tested");
        }
        
        System.out.println("Responses not received: " + failedMessagesSum + " out of " + totalMessages);
        System.out.println();
    }

    @Override
    public void connectionClosed(int track) {
        System.out.println("Connection Closed: " + track);
    }
}
