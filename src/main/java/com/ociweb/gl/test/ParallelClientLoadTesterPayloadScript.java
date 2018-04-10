package com.ociweb.gl.test;

import com.ociweb.gl.api.Writable;
import com.ociweb.pronghorn.pipe.ChannelWriter;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class ParallelClientLoadTesterPayloadScript implements Writable {
    private final byte[][] scripts;
    private int current = 0;

    public ParallelClientLoadTesterPayloadScript(String scriptFile) {
        this(generateScripts(scriptFile));
    }

    public ParallelClientLoadTesterPayloadScript(Class resourceClass, String resourcePath) {
        this(generateScripts(resourceClass, resourcePath));
    }

    public ParallelClientLoadTesterPayloadScript(String[] scripts) {
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

    private static String[] generateScripts(String scriptFile) {
        try {
            InputStreamReader reader = new InputStreamReader(new FileInputStream(scriptFile));
            return generateScripts(reader);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static String[] generateScripts(Class resourceClass, String resourcePath) {
        InputStreamReader reader = new InputStreamReader(resourceClass.getResourceAsStream(resourcePath));
        return generateScripts(reader);
    }

    private static String[] generateScripts(InputStreamReader streamReader) {
        String[] scripts;
        BufferedReader reader = new BufferedReader(streamReader);
        List<String> list = new ArrayList<>();
        try {
            String text;
            while ((text = reader.readLine()) != null) {
                list.add(text);
            }
            scripts = list.toArray(new String[list.size()]);
        }
        catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        finally {
            try {
                reader.close();
            } catch (IOException ignored) {
            }
        }
        return scripts;
    }
}
