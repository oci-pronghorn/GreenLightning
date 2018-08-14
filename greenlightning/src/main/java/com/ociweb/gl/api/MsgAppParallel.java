package com.ociweb.gl.api;

public interface MsgAppParallel<B extends Builder, G extends MsgRuntime> extends MsgApp<B, G> {

    public void declareParallelBehavior(G runtime);

}
