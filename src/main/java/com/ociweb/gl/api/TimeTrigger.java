package com.ociweb.gl.api;

/**
 * Enumeration for handling trigger rates of time-based events.
 *
 * @author Nathan Tippy
 */
public enum TimeTrigger {

    OnTheSecond(1_000),
    OnTheMinute(60_000),
    OnThe10Minute(600_000),
    OnThe15Minute(900_000),
    OnThe20Minute(1200_000),
    OnTheHour(3600_000);

    private final long rate;

    /**
     * Create a new time trigger.
     *
     * @param rate Rate, in milliseconds, that the event bound to this enumeration should be triggered.
     */
    TimeTrigger(long rate) {
        this.rate = rate;
    }

    /**
     * @return Rate, in milliseconds, that events bound to this enumeration will be triggered.
     */
    public long getRate() {
        return rate;
    }
}
