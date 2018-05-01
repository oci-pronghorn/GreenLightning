package com.ociweb.gl.impl.schema;

import com.ociweb.pronghorn.pipe.FieldReferenceOffsetManager;
import com.ociweb.pronghorn.pipe.MessageSchema;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeReader;
import com.ociweb.pronghorn.pipe.PipeWriter;
public class TrafficOrderSchema extends MessageSchema<TrafficOrderSchema> {

	public final static FieldReferenceOffsetManager FROM = new FieldReferenceOffsetManager(
		    new int[]{0xc0400003,0x80000000,0x80000001,0xc0200003,0xc0400002,0x90000000,0xc0200002,0xc0400002,0x90000001,0xc0200002},
		    (short)0,
		    new String[]{"Go","PipeIdx","Count",null,"BlockChannel","DurationNanos",null,"BlockChannelUntil",
		    "TimeMS",null},
		    new long[]{10, 11, 12, 0, 22, 13, 0, 23, 14, 0},
		    new String[]{"global",null,null,null,"global",null,null,"global",null,null},
		    "TrafficOrderSchema.xml",
		    new long[]{2, 2, 0},
		    new int[]{2, 2, 0});
    
    public static final TrafficOrderSchema instance = new TrafficOrderSchema();
    
    private TrafficOrderSchema() {
    	super(FROM);
    }

    public static final int MSG_GO_10 = 0x00000000; //Group/OpenTempl/3
    public static final int MSG_GO_10_FIELD_PIPEIDX_11 = 0x00000001; //IntegerUnsigned/None/0
    public static final int MSG_GO_10_FIELD_COUNT_12 = 0x00000002; //IntegerUnsigned/None/1
    public static final int MSG_BLOCKCHANNEL_22 = 0x00000004; //Group/OpenTempl/2
    public static final int MSG_BLOCKCHANNEL_22_FIELD_DURATIONNANOS_13 = 0x00800001; //LongUnsigned/None/0
    public static final int MSG_BLOCKCHANNELUNTIL_23 = 0x00000007; //Group/OpenTempl/2
    public static final int MSG_BLOCKCHANNELUNTIL_23_FIELD_TIMEMS_14 = 0x00800001; //LongUnsigned/None/1

    /**
     *
     * @param input Pipe<TrafficOrderSchema> arg used in PipeReader.getMsgIdx, consumeGo, consumeBlockChannel, consumeBlockChannelUntil and PipeReader.releaseReadLock
     */
    public static void consume(Pipe<TrafficOrderSchema> input) {
        while (PipeReader.tryReadFragment(input)) {
            int msgIdx = PipeReader.getMsgIdx(input);
            switch(msgIdx) {
                case MSG_GO_10:
                    consumeGo(input);
                break;
                case MSG_BLOCKCHANNEL_22:
                    consumeBlockChannel(input);
                break;
                case MSG_BLOCKCHANNELUNTIL_23:
                    consumeBlockChannelUntil(input);
                break;
                case -1:
                   //requestShutdown();
                break;
            }
            PipeReader.releaseReadLock(input);
        }
    }

    /**
     *
     * @param input Pipe<TrafficOrderSchema> arg used in PipeReader.readInt
     */
    public static void consumeGo(Pipe<TrafficOrderSchema> input) {
        int fieldPipeIdx = PipeReader.readInt(input,MSG_GO_10_FIELD_PIPEIDX_11);
        int fieldCount = PipeReader.readInt(input,MSG_GO_10_FIELD_COUNT_12);
    }

    /**
     *
     * @param input Pipe<TrafficOrderSchema> arg used in PipeReader.readLong
     */
    public static void consumeBlockChannel(Pipe<TrafficOrderSchema> input) {
        long fieldDurationNanos = PipeReader.readLong(input,MSG_BLOCKCHANNEL_22_FIELD_DURATIONNANOS_13);
    }

    /**
     *
     * @param input Pipe<TrafficOrderSchema> arg used in PipeReader.readLong
     */
    public static void consumeBlockChannelUntil(Pipe<TrafficOrderSchema> input) {
        long fieldTimeMS = PipeReader.readLong(input,MSG_BLOCKCHANNELUNTIL_23_FIELD_TIMEMS_14);
    }

    /**
     *
     * @param output Pipe<TrafficOrderSchema> arg used in PipeWriter.presumeWriteFragment, .writeInt, .publishWrites
     * @param fieldPipeIdx int arg used in PipeWriter.writeInt
     * @param fieldCount int arg used in PipeWriter.writeInt
     */
    public static void publishGo(Pipe<TrafficOrderSchema> output, int fieldPipeIdx, int fieldCount) {
            PipeWriter.presumeWriteFragment(output, MSG_GO_10);
            PipeWriter.writeInt(output,MSG_GO_10_FIELD_PIPEIDX_11, fieldPipeIdx);
            PipeWriter.writeInt(output,MSG_GO_10_FIELD_COUNT_12, fieldCount);
            PipeWriter.publishWrites(output);
    }

    /**
     *
     * @param output Pipe<TrafficOrderSchema> arg used in PipeWriter.presumeWriteFragment, .writeLong and .publishWrites
     * @param fieldDurationNanos long arg in nanoseconds used in PipeWriter.writeLong
     */
    public static void publishBlockChannel(Pipe<TrafficOrderSchema> output, long fieldDurationNanos) {
            PipeWriter.presumeWriteFragment(output, MSG_BLOCKCHANNEL_22);
            PipeWriter.writeLong(output,MSG_BLOCKCHANNEL_22_FIELD_DURATIONNANOS_13, fieldDurationNanos);
            PipeWriter.publishWrites(output);
    }

    /**
     *
     * @param output Pipe<TrafficOrderSchema> arg used in PipeWriter.presumeWriteFragment, .writeLong and .publishWrites
     * @param fieldTimeMS long arg in milliseconds used in PipeWriter.writeLong
     */
    public static void publishBlockChannelUntil(Pipe<TrafficOrderSchema> output, long fieldTimeMS) {
            PipeWriter.presumeWriteFragment(output, MSG_BLOCKCHANNELUNTIL_23);
            PipeWriter.writeLong(output,MSG_BLOCKCHANNELUNTIL_23_FIELD_TIMEMS_14, fieldTimeMS);
            PipeWriter.publishWrites(output);
    }
        
}
