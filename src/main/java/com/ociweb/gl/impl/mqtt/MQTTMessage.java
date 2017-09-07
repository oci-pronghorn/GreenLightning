package com.ociweb.gl.impl.mqtt;

import com.ociweb.gl.api.Writable;

public class MQTTMessage {
    public CharSequence externalTopic;
    public CharSequence internalTopic;
    public int retain;
    public int qos;
    public Writable payload;

    @Override
    public String toString() {
        return String.format("%s -> %s %d %d", internalTopic, externalTopic, retain, qos);
    }
}