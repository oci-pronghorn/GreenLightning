package com.ociweb.gl.impl.schema;

import com.ociweb.pronghorn.pipe.FieldReferenceOffsetManager;
import com.ociweb.pronghorn.pipe.MessageSchema;
public class TrafficReleaseSchema extends MessageSchema<TrafficReleaseSchema> {

    public final static FieldReferenceOffsetManager FROM = new FieldReferenceOffsetManager(
            new int[]{0xc0400002,0x80000000,0xc0200002},
            (short)0,
            new String[]{"Release","Count",null},
            new long[]{20, 22, 0},
            new String[]{"global",null,null},
            "TrafficReleaseSchema.xml",
            new long[]{2, 2, 0},
            new int[]{2, 2, 0});
        
    public static final int MSG_RELEASE_20 = 0x00000000;
    public static final int MSG_RELEASE_20_FIELD_COUNT_22 = 0x00000001;
    
    public static final TrafficReleaseSchema instance = new TrafficReleaseSchema();
    
    private TrafficReleaseSchema() {
        super(FROM);
    }
        
}
