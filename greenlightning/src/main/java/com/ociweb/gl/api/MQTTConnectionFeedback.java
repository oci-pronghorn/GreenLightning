package com.ociweb.gl.api;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public class MQTTConnectionFeedback implements Externalizable {
    public MQTTConnectionStatus status;
    public boolean sessionPresent;

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeInt(status.getSpecification());
        out.writeBoolean(sessionPresent);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        status = MQTTConnectionStatus.fromSpecification(in.readInt());
        sessionPresent = in.readBoolean();
    }

    @Override
    public String toString() {
        if (status == MQTTConnectionStatus.connected) {
            return String.format("Connect Feedback: Success Present %b", sessionPresent);
        }
        return String.format("Connect Feedback: %s", status);
    }
}
