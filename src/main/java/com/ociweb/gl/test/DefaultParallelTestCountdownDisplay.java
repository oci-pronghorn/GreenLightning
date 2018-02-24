package com.ociweb.gl.test;

import com.ociweb.pronghorn.stage.scheduling.ElapsedTimeRecorder;

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
    public void displayTrackEnd(int enderCounter) {
        System.out.println("Ender " + enderCounter);
    }

    @Override
    public void displayEnd(ElapsedTimeRecorder etr, int totalMessages, int failedMessagesSum) {
        System.out.println();
        etr.report(System.out).append("\n");
        System.out.println("Responses not received: " + failedMessagesSum + " out of " + totalMessages);
        System.out.println();
    }
}
