package com.ociweb.gl.impl.schema;

import com.ociweb.pronghorn.pipe.FieldReferenceOffsetManager;
import com.ociweb.pronghorn.pipe.MessageSchema;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeReader;
import com.ociweb.pronghorn.pipe.PipeWriter;
public class TrafficAckSchema extends MessageSchema<TrafficAckSchema> {

    public final static FieldReferenceOffsetManager FROM = new FieldReferenceOffsetManager(
            new int[]{0xc0400001,0xc0200001},
            (short)0,
            new String[]{"Done",null},
            new long[]{10, 0},
            new String[]{"global",null},
            "TrafficAckSchema.xml",
            new long[]{2, 2, 0},
            new int[]{2, 2, 0});
    

    
    public static final TrafficAckSchema instance = new TrafficAckSchema();
    
    private TrafficAckSchema() {
        super(FROM);
    }
        
    public static final int MSG_DONE_10 = 0x00000000;

    /**
     *
     * @param input Pipe<TrafficSchema> arg used for PipeReader.tryReadFragment, PipeReader.getMsgIdx, consumeDone and PipeReader.releaseReadLock
     */
    public static void consume(Pipe<TrafficAckSchema> input) {
        while (PipeReader.tryReadFragment(input)) {
            int msgIdx = PipeReader.getMsgIdx(input);
            switch(msgIdx) {
                case MSG_DONE_10:
                    consumeDone(input);
                break;
                case -1:
                   //requestShutdown();
                break;
            }
            PipeReader.releaseReadLock(input);
        }
    }

    public static void consumeDone(Pipe<TrafficAckSchema> input) {
    }

    /**
     * Pipe<TrafficSchema> arg used for PipeWriter.presumeWriteFragment and .publishWrites
     * @param output
     */
    public static void publishDone(Pipe<TrafficAckSchema> output) {
            PipeWriter.presumeWriteFragment(output, MSG_DONE_10);
            PipeWriter.publishWrites(output);
    }
}
