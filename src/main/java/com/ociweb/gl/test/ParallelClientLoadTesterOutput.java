package com.ociweb.gl.test;

import com.ociweb.pronghorn.stage.scheduling.ElapsedTimeRecorder;

public interface ParallelClientLoadTesterOutput {

    void progress(int pctDone, int sumFail, int sumInvalid);

    void end(
            ElapsedTimeRecorder etr, long testDuration, long totalMessages, long totalTimeSumNS, long serverCallsPerSecond,
            int sendAttempts, int sendFailures, int timeouts, int responsesReceived, int invalidResponses);

    void connectionClosed(int track);

    void failedToStart(int maxInFlight);
}

