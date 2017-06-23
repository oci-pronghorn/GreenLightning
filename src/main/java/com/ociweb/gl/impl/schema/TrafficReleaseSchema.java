package com.ociweb.gl.impl.schema;

import com.ociweb.pronghorn.pipe.FieldReferenceOffsetManager;
import com.ociweb.pronghorn.pipe.MessageSchema;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeReader;
import com.ociweb.pronghorn.pipe.PipeWriter;
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
        
    public static final TrafficReleaseSchema instance = new TrafficReleaseSchema();
    
    private TrafficReleaseSchema() {
    	super(FROM);
    }

    public static final int MSG_RELEASE_20 = 0x00000000;
    public static final int MSG_RELEASE_20_FIELD_COUNT_22 = 0x00000001;


    public static void consume(Pipe<TrafficReleaseSchema> input) {
        while (PipeReader.tryReadFragment(input)) {
            int msgIdx = PipeReader.getMsgIdx(input);
            switch(msgIdx) {
                case MSG_RELEASE_20:
                    consumeRelease(input);
                break;
                case -1:
                   //requestShutdown();
                break;
            }
            PipeReader.releaseReadLock(input);
        }
    }

    public static void consumeRelease(Pipe<TrafficReleaseSchema> input) {
        int fieldCount = PipeReader.readInt(input,MSG_RELEASE_20_FIELD_COUNT_22);
    }

    public static void publishRelease(Pipe<TrafficReleaseSchema> output, int fieldCount) {
            PipeWriter.presumeWriteFragment(output, MSG_RELEASE_20);
            PipeWriter.writeInt(output,MSG_RELEASE_20_FIELD_COUNT_22, fieldCount);
            PipeWriter.publishWrites(output);
    }
    
}
