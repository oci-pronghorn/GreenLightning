package com.ociweb.gl.impl.schema;

import com.ociweb.pronghorn.pipe.FieldReferenceOffsetManager;
import com.ociweb.pronghorn.pipe.MessageSchema;
public class TrafficAckSchema extends MessageSchema {

    public final static FieldReferenceOffsetManager FROM = new FieldReferenceOffsetManager(
            new int[]{0xc0400001,0xc0200001},
            (short)0,
            new String[]{"Done",null},
            new long[]{10, 0},
            new String[]{"global",null},
            "TrafficAckSchema.xml",
            new long[]{2, 2, 0},
            new int[]{2, 2, 0});
    
    public static final int MSG_DONE_10 = 0x00000000;

    
    public static final TrafficAckSchema instance = new TrafficAckSchema();
    
    private TrafficAckSchema() {
        super(FROM);
    }
        
}
