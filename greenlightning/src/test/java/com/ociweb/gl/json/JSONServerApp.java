package com.ociweb.gl.json;
import com.ociweb.gl.api.GreenApp;
import com.ociweb.gl.api.GreenFramework;
import com.ociweb.gl.api.GreenRuntime;

public class JSONServerApp implements GreenApp {
    private int InventoryLocationOrgcodRouteId;
    private long flagsFieldId;
    
    // Declare connections
    @Override
    public void declareConfiguration(GreenFramework builder) {
        // Setup the server
        builder.useHTTP1xServer(8068).setHost("127.0.0.1")
                .useInsecureServer()
                .setDecryptionUnitsPerTrack(2)
                .setEncryptionUnitsPerTrack(2)
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
        
        flagsFieldId = builder.lookupFieldByName(InventoryLocationOrgcodRouteId, "flag");
        
        
    }

    // Declare business logic
    @Override
    public void declareBehavior(GreenRuntime runtime) {
        JSONServerBehavior restListener = new JSONServerBehavior(runtime, flagsFieldId);
        runtime.registerListener(restListener).includeRoutes(InventoryLocationOrgcodRouteId);

    }

}
