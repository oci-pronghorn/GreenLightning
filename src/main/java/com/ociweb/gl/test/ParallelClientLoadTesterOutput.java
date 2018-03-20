package com.ociweb.gl.test;

import com.ociweb.pronghorn.stage.scheduling.ElapsedTimeRecorder;

public interface ParallelClientLoadTesterOutput {

    void failedToStart(int maxInFlight);

    void progress(int percentDone, long sumTimeouts, long sumInvalid);

    void connectionClosed(int track);

    void end(
            ElapsedTimeRecorder etr, long testDuration, long totalMessages, long totalTimeSumNS, long serverCallsPerSecond,
            long sendAttempts, long sendFailures, long timeouts, long responsesReceived, long invalidResponses);
}

