package com.ociweb.gl.impl.schema;

import com.ociweb.pronghorn.pipe.*;

import java.nio.ByteBuffer;

public class IngressMessages extends MessageSchema<IngressMessages> {

	public final static FieldReferenceOffsetManager FROM = new FieldReferenceOffsetManager(
		    new int[]{0xc0400003,0xa8000000,0xb8000001,0xc0200003},
		    (short)0,
		    new String[]{"Publish","Topic","Payload",null},
		    new long[]{103, 1, 3, 0},
		    new String[]{"global",null,null,null},
		    "IngressMessages.xml",
		    new long[]{2, 2, 0},
		    new int[]{2, 2, 0});

		protected IngressMessages() { 
		    super(FROM);
		}

		public static final IngressMessages instance = new IngressMessages();
		
		public static final int MSG_PUBLISH_103 = 0x00000000;
		public static final int MSG_PUBLISH_103_FIELD_TOPIC_1 = 0x01400001;
		public static final int MSG_PUBLISH_103_FIELD_PAYLOAD_3 = 0x01c00003;

	/**
	 *
	 * @param input Pipe<IngressMessages> arg used in consumePublish and PipeReader.releaseReadLock
	 */
		public static void consume(Pipe<IngressMessages> input) {
		    while (PipeReader.tryReadFragment(input)) {
		        int msgIdx = PipeReader.getMsgIdx(input);
		        switch(msgIdx) {
		            case MSG_PUBLISH_103:
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
	 * @param input Pipe<IngressMessages> arg used in PipeReader.readUTF8 and PipeReader.readBytes
	 */
		public static void consumePublish(Pipe<IngressMessages> input) {
		    StringBuilder fieldTopic = PipeReader.readUTF8(input,MSG_PUBLISH_103_FIELD_TOPIC_1,new StringBuilder(PipeReader.readBytesLength(input,MSG_PUBLISH_103_FIELD_TOPIC_1)));
		    ByteBuffer fieldPayload = PipeReader.readBytes(input,MSG_PUBLISH_103_FIELD_PAYLOAD_3,ByteBuffer.allocate(PipeReader.readBytesLength(input,MSG_PUBLISH_103_FIELD_PAYLOAD_3)));
		}

	/**
	 *
	 * @param output Pipe<IngressMessages> used in PipeWriter.presumeWriteFragment, writeUTF8, writeBytes and publishWrites
	 * @param fieldTopic CharSequence arg used in PipeWriter.writeUTF8
	 * @param fieldPayloadBacking byte[] arg used in PipeWriter.writeBytes
	 * @param fieldPayloadPosition int arg used in PipeWriter.writeBytes
	 * @param fieldPayloadLength int arg used in PipeWriter.writeBytes
	 */
		public static void publishPublish(Pipe<IngressMessages> output, CharSequence fieldTopic, byte[] fieldPayloadBacking, int fieldPayloadPosition, int fieldPayloadLength) {
		        PipeWriter.presumeWriteFragment(output, MSG_PUBLISH_103);
		        PipeWriter.writeUTF8(output,MSG_PUBLISH_103_FIELD_TOPIC_1, fieldTopic);
		        PipeWriter.writeBytes(output,MSG_PUBLISH_103_FIELD_PAYLOAD_3, fieldPayloadBacking, fieldPayloadPosition, fieldPayloadLength);
		        PipeWriter.publishWrites(output);
		}
		

}
