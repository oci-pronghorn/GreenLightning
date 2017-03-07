package com.ociweb.gl.impl.schema;

import com.ociweb.pronghorn.pipe.FieldReferenceOffsetManager;
import com.ociweb.pronghorn.pipe.MessageSchema;
public class TrafficOrderSchema extends MessageSchema {

    public final static FieldReferenceOffsetManager FROM = new FieldReferenceOffsetManager(
            new int[]{0xc0400003,0x80000000,0x80000001,0xc0200003},
            (short)0,
            new String[]{"Go","PipeIdx","Count",null},
            new long[]{10, 11, 12, 0},
            new String[]{"global",null,null,null},
            "TrafficOrderSchema.xml",
            new long[]{2, 2, 0},
            new int[]{2, 2, 0});
    
    public static final int MSG_GO_10 = 0x00000000;
    public static final int MSG_GO_10_FIELD_PIPEIDX_11 = 0x00000001;
    public static final int MSG_GO_10_FIELD_COUNT_12 = 0x00000002;
    
    
    public static final TrafficOrderSchema instance = new TrafficOrderSchema();
    
    private TrafficOrderSchema() {
        super(FROM);
    }
        
}
