package com.ociweb.gl.json;
import com.ociweb.gl.api.Builder;
import com.ociweb.gl.api.GreenAppParallel;
import com.ociweb.gl.api.GreenRuntime;

public class JSONServerApp implements GreenAppParallel {
    private int InventoryLocationOrgcodRouteId;
    // Declare connections
    @Override
    public void declareConfiguration(Builder builder) {
        // Setup the server
        builder.useHTTP1xServer(8088).setHost("127.0.0.1")
                .useInsecureServer()
                .setDecryptionUnitsPerTrack(8)
                .setEncryptionUnitsPerTrack(4)
                .setConcurrentChannelsPerDecryptUnit(2)
                .setConcurrentChannelsPerEncryptUnit(2);

        // Enable low-overhead instrumentation
        builder.enableTelemetry();

        // Create the LocationAtP API
        InventoryLocationOrgcodRouteId = JSONServerBehavior.defineRoute(builder);

        // This declares that the declareParallelBehavior method gets executed n times.
        // The scheduler will load balance the behaviors
        //builder.parallelism(4);

        builder.usePrivateTopicsExclusively();
    }

    // Declare business logic
    @Override
    public void declareBehavior(GreenRuntime runtime) {
        JSONServerBehavior restListener = new JSONServerBehavior(runtime);
        runtime.registerListener(restListener).includeRoutes(InventoryLocationOrgcodRouteId); // POST
    }

    @Override
    public void declareParallelBehavior(GreenRuntime runtime) {
        //locationAtpAPI.declareBehavior(runtime);
    }
}
