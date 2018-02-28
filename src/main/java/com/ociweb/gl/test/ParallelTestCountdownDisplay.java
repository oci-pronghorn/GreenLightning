package com.ociweb.gl.test;

import com.ociweb.pronghorn.stage.scheduling.ElapsedTimeRecorder;

public interface ParallelTestCountdownDisplay {
    enum Response {
        ResponseReveived,
        ResponseIgnored,
        ResponseTimeout,
    }
    void display(int track, int total, int current, Response response);

    void displayTrackEnd(int enderCounter);

    void displayEnd(ElapsedTimeRecorder etr, int totalMessages, int failedMessagesSum);

    void displayConnectionClosed(int track);
}

