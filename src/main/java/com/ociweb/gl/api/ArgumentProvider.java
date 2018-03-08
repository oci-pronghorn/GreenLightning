package com.ociweb.gl.api;

/**
 * Separate out the concern for argument fetching
 *
 * @author David Giovannini
 */
public interface ArgumentProvider {
    String[] args();

    Boolean getArgumentValue(String longName, String shortName, Boolean defaultValue);

    Character getArgumentValue(String longName, String shortName, Character defaultValue);

    Byte getArgumentValue(String longName, String shortName, Byte defaultValue);

    Short getArgumentValue(String longName, String shortName, Short defaultValue);

    Long getArgumentValue(String longName, String shortName, Long defaultValue);

    Integer getArgumentValue(String longName, String shortName, Integer defaultValue);

    String getArgumentValue(String longName, String shortName, String defaultValue);

    boolean hasArgument(String longName, String shortName);
}

