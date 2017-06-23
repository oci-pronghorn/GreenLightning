package com.ociweb.gl.impl.schema;

import com.ociweb.pronghorn.pipe.FieldReferenceOffsetManager;
import com.ociweb.pronghorn.pipe.MessageSchema;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeReader;
import com.ociweb.pronghorn.pipe.PipeWriter;
public class TrafficOrderSchema extends MessageSchema<TrafficOrderSchema> {

    public final static FieldReferenceOffsetManager FROM = new FieldReferenceOffsetManager(
            new int[]{0xc0400003,0x80000000,0x80000001,0xc0200003},
            (short)0,
            new String[]{"Go","PipeIdx","Count",null},
            new long[]{10, 11, 12, 0},
            new String[]{"global",null,null,null},
            "TrafficOrderSchema.xml",
            new long[]{2, 2, 0},
            new int[]{2, 2, 0});
    
    public static final TrafficOrderSchema instance = new TrafficOrderSchema();
    
    private TrafficOrderSchema() {
    	super(FROM);
    }

    public static final int MSG_GO_10 = 0x00000000;
    public static final int MSG_GO_10_FIELD_PIPEIDX_11 = 0x00000001;
    public static final int MSG_GO_10_FIELD_COUNT_12 = 0x00000002;


    public static void consume(Pipe<TrafficOrderSchema> input) {
        while (PipeReader.tryReadFragment(input)) {
            int msgIdx = PipeReader.getMsgIdx(input);
            switch(msgIdx) {
                case MSG_GO_10:
                    consumeGo(input);
                break;
                case -1:
                   //requestShutdown();
                break;
            }
            PipeReader.releaseReadLock(input);
        }
    }

    public static void consumeGo(Pipe<TrafficOrderSchema> input) {
        int fieldPipeIdx = PipeReader.readInt(input,MSG_GO_10_FIELD_PIPEIDX_11);
        int fieldCount = PipeReader.readInt(input,MSG_GO_10_FIELD_COUNT_12);
    }

    public static void publishGo(Pipe<TrafficOrderSchema> output, int fieldPipeIdx, int fieldCount) {
            PipeWriter.presumeWriteFragment(output, MSG_GO_10);
            PipeWriter.writeInt(output,MSG_GO_10_FIELD_PIPEIDX_11, fieldPipeIdx);
            PipeWriter.writeInt(output,MSG_GO_10_FIELD_COUNT_12, fieldCount);
            PipeWriter.publishWrites(output);
    }
        
}
