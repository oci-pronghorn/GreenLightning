package com.ociweb.gl.impl.schema;

import com.ociweb.pronghorn.pipe.DataInputBlobReader;
import com.ociweb.pronghorn.pipe.FieldReferenceOffsetManager;
import com.ociweb.pronghorn.pipe.MessageSchema;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeReader;
import com.ociweb.pronghorn.pipe.PipeWriter;

public class MessagePrivate extends MessageSchema<MessagePrivate> {

	public final static FieldReferenceOffsetManager FROM = new FieldReferenceOffsetManager(
		    new int[]{0xc0400002,0xb8000000,0xc0200002},
		    (short)0,
		    new String[]{"Publish","Payload",null},
		    new long[]{1, 3, 0},
		    new String[]{"global",null,null},
		    "MessagePrivate.xml",
		    new long[]{2, 2, 0},
		    new int[]{2, 2, 0});


		protected MessagePrivate() { 
		    super(FROM);
		    
		}

		public static final MessagePrivate instance = new MessagePrivate();

		public static final int MSG_PUBLISH_1 = 0x00000000; //Group/OpenTempl/2
		public static final int MSG_PUBLISH_1_FIELD_PAYLOAD_3 = 0x01c00001; //ByteVector/None/0

	/**
	 *
	 * @param input Pipe<MessagePrivate> arg used for PipeReader.getMsgIdx and PipeReader.releaseReadLock
	 */
		public static void consume(Pipe<MessagePrivate> input) {
		    while (PipeReader.tryReadFragment(input)) {
		        int msgIdx = PipeReader.getMsgIdx(input);
		        switch(msgIdx) {
		            case MSG_PUBLISH_1:
		                consumePublish(input);
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
	 * @param input Pipe<MessagePrivate> arg used in PipeReader.inputStream
	 */
		public static void consumePublish(Pipe<MessagePrivate> input) {
		    DataInputBlobReader<MessagePrivate> fieldPayload = PipeReader.inputStream(input, MSG_PUBLISH_1_FIELD_PAYLOAD_3);
		}

	/**
	 *
	 * @param output Pipe<MessagePrivate> arg used for PipeWriter.presumeWriteFragment and PipeWriter.publishWrites
	 * @param fieldPayloadBacking byte[] arg used for PipeWriter.writeBytes
	 * @param fieldPayloadPosition int arg used for PipeWriter.writeBytes
	 * @param fieldPayloadLength int arg used for PipeWriter.writeBytes
	 */
		public static void publishPublish(Pipe<MessagePrivate> output, byte[] fieldPayloadBacking, int fieldPayloadPosition, int fieldPayloadLength) {
		        PipeWriter.presumeWriteFragment(output, MSG_PUBLISH_1);
		        PipeWriter.writeBytes(output,MSG_PUBLISH_1_FIELD_PAYLOAD_3, fieldPayloadBacking, fieldPayloadPosition, fieldPayloadLength);
		        PipeWriter.publishWrites(output);
		}

		
}
