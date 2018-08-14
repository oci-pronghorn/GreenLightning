package com.ociweb.gl.api;

/**
 * Separate out the concern for argument fetching
 *
 * @author David Giovannini
 */
public interface ArgumentProvider {
    /**
     * @return all strings on command line
     */
    String[] args();

    /**
     * parses named boolean value on command line
     * @param longName full name for key of value on command line
     * @param shortName short name for key of value on command line
     * @param defaultValue value to use if not specified on command line
     * @return value on command line or default value
     */
    Boolean getArgumentValue(String longName, String shortName, Boolean defaultValue);

    /**
     * parses named Character value on command line
     * @param longName full name for key of value on command line
     * @param shortName short name for key of value on command line
     * @param defaultValue value to use if not specified on command line
     * @return value on command line or default value
     */
    Character getArgumentValue(String longName, String shortName, Character defaultValue);

    /**
     * parses named Byte value on command line
     * @param longName full name for key of value on command line
     * @param shortName short name for key of value on command line
     * @param defaultValue value to use if not specified on command line
     * @return value on command line or default value
     */
    Byte getArgumentValue(String longName, String shortName, Byte defaultValue);

    /**
     * parses named Short value on command line
     * @param longName full name for key of value on command line
     * @param shortName short name for key of value on command line
     * @param defaultValue value to use if not specified on command line
     * @return value on command line or default value
     */
    Short getArgumentValue(String longName, String shortName, Short defaultValue);

    /**
     * parses named Long value on command line
     * @param longName full name for key of value on command line
     * @param shortName short name for key of value on command line
     * @param defaultValue value to use if not specified on command line
     * @return value on command line or default value
     */
    Long getArgumentValue(String longName, String shortName, Long defaultValue);

    /**
     * parses named Integer value on command line
     * @param longName full name for key of value on command line
     * @param shortName short name for key of value on command line
     * @param defaultValue value to use if not specified on command line
     * @return value on command line or default value
     */
    Integer getArgumentValue(String longName, String shortName, Integer defaultValue);

    /**
     * parses named String value on command line
     * @param longName full name for key of value on command line
     * @param shortName short name for key of value on command line
     * @param defaultValue value to use if not specified on command line
     * @return value on command line or default value
     */
    String getArgumentValue(String longName, String shortName, String defaultValue);

    /**
     * parses named enum value on command line
     * @param <T> the specific enum type
     * @param longName full name for key of value on command line
     * @param shortName short name for key of value on command line
     * @param c class type of the enum
     * @param defaultValue value to use if not specified on command line
     * @return value on command line or default value
     */
    <T extends Enum<T>> T getArgumentValue(String longName, String shortName, Class<T> c, T defaultValue);

    /**
     * determines boolean from short or long name arg
     * @param longName full name for key of value on command line
     * @param shortName short name for key of value on command line
     * @return true if key is present
     */
    boolean hasArgument(String longName, String shortName);
}

