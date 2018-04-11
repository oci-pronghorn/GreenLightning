package com.ociweb;

import com.ociweb.gl.api.ArgumentParser;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.test.ParallelClientLoadTester;
import com.ociweb.gl.test.ParallelClientLoadTesterConfig;
import com.ociweb.gl.test.ParallelClientLoadTesterPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LightningRod {

    static final Logger logger = LoggerFactory.getLogger(GreenLightning.class);

    public static void main(String[] args) {
        ArgumentParser parser = new ArgumentParser(args);
        ParallelClientLoadTesterConfig config = new ParallelClientLoadTesterConfig(parser);
        ParallelClientLoadTesterPayload payload = new ParallelClientLoadTesterPayload(parser);

        GreenRuntime.run(new ParallelClientLoadTester(config, payload), args);
    }
}
