package com.ociweb.gl.api;

public class ArgumentParser implements ArgumentProvider {
    private final String[] args;

    public ArgumentParser(String[] args) {
        this.args = args;
    }

    @Override
    public String[] args() {
        return args;
    }

    @Override
    public String getArgumentValue(String longName, String shortName, String defaultValue) {
        return getOptArg(longName, shortName, defaultValue);
    }

    @Override
    public Character getArgumentValue(String longName, String shortName, Character defaultValue) {
        String value = getOptArg(longName, shortName, defaultValue!=null?defaultValue.toString():null);
        return value!=null?value.charAt(0):null;
    }

    @Override
    public Byte getArgumentValue(String longName, String shortName, Byte defaultValue) {
        String value = getOptArg(longName, shortName, defaultValue!=null?defaultValue.toString():null);
        return value!=null?Byte.parseByte(value):null;
    }

    @Override
    public Short getArgumentValue(String longName, String shortName, Short defaultValue) {
        String value = getOptArg(longName, shortName, defaultValue!=null?defaultValue.toString():null);
        return value!=null?Short.parseShort(value):null;
    }

    @Override
    public Long getArgumentValue(String longName, String shortName, Long defaultValue) {
        String value = getOptArg(longName, shortName, defaultValue!=null?defaultValue.toString():null);
        return value!=null?Long.parseLong(value):null;
    }

    @Override
    public Integer getArgumentValue(String longName, String shortName, Integer defaultValue) {
        String value = getOptArg(longName, shortName, defaultValue!=null?defaultValue.toString():null);
        return value!=null?Integer.parseInt(value):null;
    }

    @Override
    public Boolean getArgumentValue(String longName, String shortName, Boolean defaultValue) {
        String value = getOptArg(longName, shortName, defaultValue!=null?defaultValue.toString():null);
        return value!=null?Boolean.parseBoolean(value):null;
    }

    @Override
    public boolean hasArgument(String longName, String shortName) {
        return hasArg(longName, shortName);
    }

    private String getOptArg(String longName, String shortName, String defaultValue) {
        String prev = null;
        for (String token : args) {
            if (longName.equals(prev) || shortName.equals(prev)) {
                if (token == null || token.trim().length() == 0 || token.startsWith("-")) {
                    return reportChoice(longName, shortName, defaultValue);
                }
                return reportChoice(longName, shortName, token.trim(), defaultValue);
            }
            prev = token;
        }
        return reportChoice(longName, shortName, defaultValue);
    }


    private boolean hasArg(String longName, String shortName) {
        for(String token : args) {
            if(longName.equals(token) || shortName.equals(token)) {
                return reportChoice(longName, shortName, true);
            }
        }
        return reportChoice(longName, shortName, false);
    }

    private String reportChoice(final String longName, final String shortName, final String dflt) {
        System.out.append(longName).append(" ").append(shortName).append(" = ").append(dflt).append("\n");
        return dflt;
    }

    private String reportChoice(final String longName, final String shortName, final String value, final String dflt) {
        if (value.equals(dflt)) {
            return reportChoice(longName, shortName, dflt);
        }
        System.out.append(longName).append(" ").append(shortName).append(" = ").append(value).append(" (default = ").append(dflt).append(")\n");
        return value;
    }

    private boolean reportChoice(final String longName, final String shortName, boolean found) {
        System.out.append(longName).append(" ").append(shortName).append(" found ").append(Boolean.toString(found)).append("\n");
        return found;
    }
}
