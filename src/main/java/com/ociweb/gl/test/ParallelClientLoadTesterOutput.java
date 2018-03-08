package com.ociweb.gl.test;

import com.ociweb.pronghorn.stage.scheduling.ElapsedTimeRecorder;

public interface ParallelClientLoadTesterOutput {

    void progress(int pctDone, int sumFail);

    void end(ElapsedTimeRecorder etr, int totalMessages, long totalTimeSum, int failedMessagesSum, long duration, long serverCallsPerSecond);

    void connectionClosed(int track);
}

