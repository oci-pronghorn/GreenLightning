package com.ociweb.gl.test;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.PubSubFixedTopicService;
import com.ociweb.gl.api.PubSubMethodListener;
import com.ociweb.gl.api.StartupListener;
import com.ociweb.pronghorn.pipe.ChannelReader;
import com.ociweb.pronghorn.stage.scheduling.ElapsedTimeRecorder;

class LoadTestProgress implements PubSubMethodListener, StartupListener {

	private final ParallelClientLoadTester parallelClientLoadTester;

	private final PubSubFixedTopicService cmd4;

	private final long[] finished;
	private final long[] timeouts;
	private final long[] responsesInvalid;

	private long totalTimeSum;
	private long sendAttemptsSum;
	private long sendFailuresSum;
	private long timeoutsSum;
	private long responsesReceivedSum;
	private long responsesInvalidSum;

	private int lastPercent = 0;
	private long lastTime = 0;
	private int enderCounter;
	private boolean done = false;

	LoadTestProgress(ParallelClientLoadTester parallelClientLoadTester, GreenRuntime runtime) {
		this.parallelClientLoadTester = parallelClientLoadTester;
		this.cmd4 = runtime.newCommandChannel().newPubSubService(
				                                    ParallelClientLoadTester.ENDERS_TOPIC,
				                                    Math.max(ParallelClientLoadTester.PUB_MSGS, this.parallelClientLoadTester.maxInFlight), ParallelClientLoadTester.PUB_MSGS_SIZE);
	
		this.finished = new long[this.parallelClientLoadTester.parallelTracks];
		this.timeouts = new long[this.parallelClientLoadTester.parallelTracks];
		this.responsesInvalid = new long[this.parallelClientLoadTester.parallelTracks];
	}

	@Override
	public void startup() {
		this.parallelClientLoadTester.startupTime = System.nanoTime();
	}

	boolean enderMessage(CharSequence topic, ChannelReader payload) {
		if (topic.equals(ParallelClientLoadTester.ENDERS_TOPIC)) {
			
			if (payload.hasRemainingBytes()) {
				int track = payload.readPackedInt();
				long totalTime = payload.readPackedLong();
				long sendAttempts = payload.readPackedLong();
				long sendFailures = payload.readPackedLong();
				long timeouts = payload.readPackedLong();
				long responsesReceived = payload.readPackedLong();
				long responsesInvalid = payload.readPackedLong();

				totalTimeSum += totalTime;
				sendAttemptsSum += sendAttempts;
				sendFailuresSum += sendFailures;
				timeoutsSum += timeouts;
				responsesReceivedSum += responsesReceived;
				responsesInvalidSum += responsesInvalid;
				
				//logger.info("Finished track {} remaining {}",track,(parallelTracks-enderCounter));
			}

			if (++enderCounter == ((this.parallelClientLoadTester.sessionCount * 
					                this.parallelClientLoadTester.parallelTracks) + 1)) { //we add 1 for the progress of 100%
				ElapsedTimeRecorder etr = new ElapsedTimeRecorder();
				int t = this.parallelClientLoadTester.elapsedTime.length;
				while (--t >= 0) {
					etr.add(this.parallelClientLoadTester.elapsedTime[t]);
				}
				
				//NOTE: these counts do include warmup
                long totalMessages = this.parallelClientLoadTester.parallelTracks
                		           * this.parallelClientLoadTester.cyclesPerTrack 
                		           * this.parallelClientLoadTester.sessionCount;
                long testDuration = System.nanoTime() - this.parallelClientLoadTester.startupTime;
                long serverCallsPerSecond = (1_000_000_000L * totalMessages) / testDuration;

				this.parallelClientLoadTester.out.end(
						etr, testDuration, totalMessages, totalTimeSum, serverCallsPerSecond,
						sendAttemptsSum, sendFailuresSum, timeoutsSum, responsesReceivedSum, responsesInvalidSum
				);
				ParallelClientLoadTester.logger.info("\nshutting down load test, all tracks have finished");
				cmd4.requestShutdown();
				return true;
			}
		}
		return true;
	}
	
	
	
	boolean progressMessage(CharSequence topic, ChannelReader payload) {
		int track = payload.readPackedInt();
		this.finished[track] = payload.readPackedLong();
		//long sendAttempts = payload.readPackedInt();
		//long sendFailures = payload.readPackedInt();
		long timeouts = payload.readPackedInt();
		//long responsesReceived = payload.readPackedInt();
		long responsesInvalid = payload.readPackedInt();
		
		//this.sendAttempts[track] = sendAttempts;
		//this.sendFailures[track] = sendFailures;
		this.timeouts[track] = timeouts;
		//this.responsesReceived[track] = responsesReceived;
		this.responsesInvalid[track] = responsesInvalid;

		long sumFinished = 0;
		//long sumSendAttempts = 0;
		//long sumSendFailures = 0;
		long sumTimeouts = 0;
		//long sumResponsesReceived = 0;
		long sumResponsesInvalid = 0;
		int trackId = this.parallelClientLoadTester.parallelTracks;
	
		while (--trackId >= 0) {
			
			sumFinished += this.finished[trackId];
			//sumSendAttempts += this.sendAttempts[track];
			//sumSendFailures += this.sendFailures[track];
			sumTimeouts += this.timeouts[track];
			//sumResponsesReceived += this.responsesReceived[i];
			sumResponsesInvalid += this.responsesInvalid[trackId];
		}

		long totalRequests = (long)this.parallelClientLoadTester.cyclesPerTrack 
				           * (long)this.parallelClientLoadTester.parallelTracks 
				           * (long)this.parallelClientLoadTester.sessionCount;
		int percentDone = (int)((100L * sumFinished) / totalRequests);
        long curDuration = System.nanoTime() - this.parallelClientLoadTester.startupTime;
        long estCallsPerSecond = (1_000_000_000L * sumFinished) / curDuration;
		assert(percentDone>=0);

		long now = 0;

		//updates no faster than once every second and only after 1% is complete.
		if (((percentDone>1) &&  (percentDone > lastPercent) && ((now = System.nanoTime()) - lastTime) > 1_000_000_000L)
				|| (100L == percentDone && percentDone!=lastPercent)
				) {
		    this.parallelClientLoadTester.out.progress(percentDone, sumTimeouts, sumResponsesInvalid, estCallsPerSecond);

			lastTime = now;
			lastPercent = percentDone;
		}
		
		//NOTE: due to done boolean we only send this value once
		if (100 == percentDone && (!done)) {
			//ParallelClientLoadTester.logger.info("received {}% of requests on all tracks",percentDone);
			boolean result = cmd4.publishTopic();	
			if (result) {
				done = true;					
			}
			return result;
		}
		return true;
	}
}