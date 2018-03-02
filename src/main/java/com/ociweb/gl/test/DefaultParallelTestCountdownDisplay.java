package com.ociweb.gl.test;

import com.ociweb.pronghorn.stage.scheduling.ElapsedTimeRecorder;
import com.ociweb.pronghorn.util.Appendables;

public class DefaultParallelTestCountdownDisplay implements ParallelTestCountdownDisplay {

    @Override
    public void display(int track, int total, int current, Response response) {
        String out = "Track " + track + ".";
        String suffix = "";
        if (response == Response.ResponseIgnored) {
            suffix = "*";
        }
        if (response == Response.ResponseTimeout) {
            suffix = "!";
        }

        if (current == total) {
            System.out.println(out + current + suffix);
        }
        else if (current >= 10_000) {
            if ((current % 10_000) == 0) {
                System.out.println(out + current + suffix);
            }
        }
        else if (current >= 1_000) {
            if ((current % 1_000) == 0) {
                System.out.println(out + current + suffix);
            }
        }
        else if (current >= 100) {
            if ((current % 100) == 0) {
                System.out.println(out + current + suffix);
            }
        }
        else if (current >= 10) {
            if ((current % 10) == 0) {
                System.out.println(out + current + suffix);
            }
        }
        else {
            System.out.println(out + current + suffix);
        }
    }

    @Override
    public void displayEnd(ElapsedTimeRecorder etr, int totalMessages, long totalTimeSumNS, int failedMessagesSum) {
        System.out.println();
        etr.report(System.out).append("\n");
        System.out.println();
        
        if (totalTimeSumNS>0) {
        	long requestsPerSecond =  (1_000_000_000L*(long)totalMessages)/totalTimeSumNS;
        	System.out.println(requestsPerSecond+" requests per second");
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
    public void displayConnectionClosed(int track) {
        System.out.println("Connection Closed: " + track);
    }
}
