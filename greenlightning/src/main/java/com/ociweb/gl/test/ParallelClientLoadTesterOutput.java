package com.ociweb.gl.test;

import com.ociweb.pronghorn.stage.scheduling.ElapsedTimeRecorder;

public interface ParallelClientLoadTesterOutput {

    void failedToStart(int maxInFlight);

    void progress(int percentDone, long sumTimeouts, long sumInvalid, long estCallsPerSec);

    void longCallDetected(int track, long duration, long now, long start);

    void timout(long responseTimeoutNS);

    void connectionClosed(int track);

    void end(
            ElapsedTimeRecorder etr, long testDuration, long totalMessages, long totalTimeSumNS, long serverCallsPerSecond,
            long sendAttempts, long sendFailures, long timeouts, long responsesReceived, long invalidResponses);

    void finishedWarmup();
}

