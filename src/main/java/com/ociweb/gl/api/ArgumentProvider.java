package com.ociweb.gl.api;

/**
 * Separate out the concern for argument fetching for 
 *
 * @author David Giovannini
 */
public interface ArgumentProvider {
    String[] args();

    String getArgumentValue(String longName, String shortName, String defaultValue);

    boolean hasArgument(String longName, String shortName);
}
