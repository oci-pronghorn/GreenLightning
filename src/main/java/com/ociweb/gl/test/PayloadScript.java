package com.ociweb.gl.test;

import com.ociweb.gl.api.Writable;
import com.ociweb.pronghorn.pipe.ChannelWriter;

public class PayloadScript implements Writable {
    private final byte[][] scripts;
    private int current = 0;

    public PayloadScript(String[] scripts) {
        this.scripts = new byte[scripts.length][];
        for (int i = 0; i < scripts.length; i++) {
            this.scripts[i] = scripts[i].getBytes();
        }
    }

    @Override
    public void write(ChannelWriter writer) {
        current++;
        if (current == scripts.length) current = 0;
        writer.write(scripts[current]);
    }
}
