package com.ociweb.gl.impl.schema;

import com.ociweb.pronghorn.pipe.DataInputBlobReader;
import com.ociweb.pronghorn.pipe.FieldReferenceOffsetManager;
import com.ociweb.pronghorn.pipe.MessageSchema;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeReader;
import com.ociweb.pronghorn.pipe.PipeWriter;
public class MessagePubSub extends MessageSchema<MessagePubSub> {


	public final static FieldReferenceOffsetManager FROM = new FieldReferenceOffsetManager(
	    new int[]{0xc0400003,0xa8000000,0x80000000,0xc0200003,0xc0400003,0xa8000000,0x80000000,0xc0200003,0xc0400004,0x80000001,0xa8000000,0xb8000001,0xc0200004,0xc0400002,0x80000002,0xc0200002},
	    (short)0,
	    new String[]{"Subscribe","Topic","SubscriberIdentityHash",null,"Unsubscribe","Topic","SubscriberIdentityHash",
	    null,"Publish","QOS","Topic","Payload",null,"ChangeState","Ordinal",null},
	    new long[]{100, 1, 4, 0, 101, 1, 4, 0, 103, 5, 1, 3, 0, 70, 7, 0},
	    new String[]{"global",null,null,null,"global",null,null,null,"global",null,null,null,null,"global",
	    null,null},
	    "MessagePubSub.xml",
	    new long[]{2, 2, 0},
	    new int[]{2, 2, 0});
    
	public static final MessagePubSub instance = new MessagePubSub();
	
	private MessagePubSub() {
		super(FROM);
	}

	public static final int MSG_SUBSCRIBE_100 = 0x00000000; //Group/OpenTempl/3
	public static final int MSG_SUBSCRIBE_100_FIELD_TOPIC_1 = 0x01400001; //UTF8/None/0
	public static final int MSG_SUBSCRIBE_100_FIELD_SUBSCRIBERIDENTITYHASH_4 = 0x00000003; //IntegerUnsigned/None/0
	public static final int MSG_UNSUBSCRIBE_101 = 0x00000004; //Group/OpenTempl/3
	public static final int MSG_UNSUBSCRIBE_101_FIELD_TOPIC_1 = 0x01400001; //UTF8/None/0
	public static final int MSG_UNSUBSCRIBE_101_FIELD_SUBSCRIBERIDENTITYHASH_4 = 0x00000003; //IntegerUnsigned/None/0
	public static final int MSG_PUBLISH_103 = 0x00000008; //Group/OpenTempl/4
	public static final int MSG_PUBLISH_103_FIELD_QOS_5 = 0x00000001; //IntegerUnsigned/None/1
	public static final int MSG_PUBLISH_103_FIELD_TOPIC_1 = 0x01400002; //UTF8/None/0
	public static final int MSG_PUBLISH_103_FIELD_PAYLOAD_3 = 0x01c00004; //ByteVector/None/1
	public static final int MSG_CHANGESTATE_70 = 0x0000000d; //Group/OpenTempl/2
	public static final int MSG_CHANGESTATE_70_FIELD_ORDINAL_7 = 0x00000001; //IntegerUnsigned/None/2

	/**
	 *
	 * @param input Pipe<MessagePubSub> arg used in PipeReader.tryReadFragment and case MSG_SUBSCRIBE_100, 101, 103, and 70
	 */
	public static void consume(Pipe<MessagePubSub> input) {
	    while (PipeReader.tryReadFragment(input)) {
	        int msgIdx = PipeReader.getMsgIdx(input);
	        switch(msgIdx) {
	            case MSG_SUBSCRIBE_100:
	                consumeSubscribe(input);
	            break;
	            case MSG_UNSUBSCRIBE_101:
	                consumeUnsubscribe(input);
	            break;
	            case MSG_PUBLISH_103:
	                consumePublish(input);
	            break;
	            case MSG_CHANGESTATE_70:
	                consumeChangeState(input);
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
	 * @param input Pipe<MessagePubSub> arg used for PipeReader.readUTF8, PipeReader.readBytesLength and PipeReader.readInt
	 */
	public static void consumeSubscribe(Pipe<MessagePubSub> input) {
	    StringBuilder fieldTopic = PipeReader.readUTF8(input,MSG_SUBSCRIBE_100_FIELD_TOPIC_1,new StringBuilder(PipeReader.readBytesLength(input,MSG_SUBSCRIBE_100_FIELD_TOPIC_1)));
	    int fieldSubscriberIdentityHash = PipeReader.readInt(input,MSG_SUBSCRIBE_100_FIELD_SUBSCRIBERIDENTITYHASH_4);
	}

	/**
	 *
	 * @param input arg used for PipeReader.readUTF8, PipeReader.readBytesLength and PipeReader.readIntPipeReader.readUTF8, PipeReader.readBytesLength and PipeReader.readInt
	 */
	public static void consumeUnsubscribe(Pipe<MessagePubSub> input) {
	    StringBuilder fieldTopic = PipeReader.readUTF8(input,MSG_UNSUBSCRIBE_101_FIELD_TOPIC_1,new StringBuilder(PipeReader.readBytesLength(input,MSG_UNSUBSCRIBE_101_FIELD_TOPIC_1)));
	    int fieldSubscriberIdentityHash = PipeReader.readInt(input,MSG_UNSUBSCRIBE_101_FIELD_SUBSCRIBERIDENTITYHASH_4);
	}

	/**
	 *
	 * @param input Pipe<MessagePubSub> arg used for PipeReader.readInt, PipeReader.readUTF8 and PipeReader.inputStream
	 */
	public static void consumePublish(Pipe<MessagePubSub> input) {
	    int fieldQOS = PipeReader.readInt(input,MSG_PUBLISH_103_FIELD_QOS_5);
	    StringBuilder fieldTopic = PipeReader.readUTF8(input,MSG_PUBLISH_103_FIELD_TOPIC_1,new StringBuilder(PipeReader.readBytesLength(input,MSG_PUBLISH_103_FIELD_TOPIC_1)));
	    DataInputBlobReader<MessagePubSub> fieldPayload = PipeReader.inputStream(input, MSG_PUBLISH_103_FIELD_PAYLOAD_3);
	}

	/**
	 *
	 * @param input Pipe<MessagePubSub> arg used for PipeReader.readInt
	 */
	public static void consumeChangeState(Pipe<MessagePubSub> input) {
	    int fieldOrdinal = PipeReader.readInt(input,MSG_CHANGESTATE_70_FIELD_ORDINAL_7);
	}

	/**
	 *
	 * @param output Pipe<MessagePubSub> arg used for PipeWriter.presumeWriteFragment, .writeUTF8, .writeInt and .publishWrites
	 * @param fieldTopic CharSequence arg used for PipeWriter.writeUTF8
	 * @param fieldSubscriberIdentityHash int arg used for PipeWriter.writeInt
	 */
	public static void publishSubscribe(Pipe<MessagePubSub> output, CharSequence fieldTopic, int fieldSubscriberIdentityHash) {
	        PipeWriter.presumeWriteFragment(output, MSG_SUBSCRIBE_100);
	        PipeWriter.writeUTF8(output,MSG_SUBSCRIBE_100_FIELD_TOPIC_1, fieldTopic);
	        PipeWriter.writeInt(output,MSG_SUBSCRIBE_100_FIELD_SUBSCRIBERIDENTITYHASH_4, fieldSubscriberIdentityHash);
	        PipeWriter.publishWrites(output);
	}

	/**
	 *
	 * @param output Pipe<MessagePubSub> arg used for PipeWriter.presumeWriteFragment
	 * @param fieldTopic CharSequence arg used for PipeWriter.writeUTF8
	 * @param fieldSubscriberIdentityHash int arg used for PipeWriter.writeInt
	 */
	public static void publishUnsubscribe(Pipe<MessagePubSub> output, CharSequence fieldTopic, int fieldSubscriberIdentityHash) {
	        PipeWriter.presumeWriteFragment(output, MSG_UNSUBSCRIBE_101);
	        PipeWriter.writeUTF8(output,MSG_UNSUBSCRIBE_101_FIELD_TOPIC_1, fieldTopic);
	        PipeWriter.writeInt(output,MSG_UNSUBSCRIBE_101_FIELD_SUBSCRIBERIDENTITYHASH_4, fieldSubscriberIdentityHash);
	        PipeWriter.publishWrites(output);
	}

	/**
	 *
	 * @param output Pipe<MessagePubSub> arg used in PipeWriter.writeInt, .writeUTF8, .writeBytes and .publishWrites
	 * @param fieldQOS int arg used in PipeWriter.writeInt
	 * @param fieldTopic CharSequence arg used in PipeWriter.writeUTF8
	 * @param fieldPayloadBacking byte[] arg used in PipeWriter.writeBytes
	 * @param fieldPayloadPosition int arg used in PipeWriter.writeBytes
	 * @param fieldPayloadLength int arg used in PipeWriter.writeBytes
	 */
	public static void publishPublish(Pipe<MessagePubSub> output, int fieldQOS, CharSequence fieldTopic, byte[] fieldPayloadBacking, int fieldPayloadPosition, int fieldPayloadLength) {
	        PipeWriter.presumeWriteFragment(output, MSG_PUBLISH_103);
	        PipeWriter.writeInt(output,MSG_PUBLISH_103_FIELD_QOS_5, fieldQOS);
	        PipeWriter.writeUTF8(output,MSG_PUBLISH_103_FIELD_TOPIC_1, fieldTopic);
	        PipeWriter.writeBytes(output,MSG_PUBLISH_103_FIELD_PAYLOAD_3, fieldPayloadBacking, fieldPayloadPosition, fieldPayloadLength);
	        PipeWriter.publishWrites(output);
	}

	/**
	 *
	 * @param output Pipe<MessagePubSub> arg used in PipeWriter.presumeWriteFragment and PipeWriter.publishWrites
	 * @param fieldOrdinal int arg used in PipeWriter.writeInt
	 */
	public static void publishChangeState(Pipe<MessagePubSub> output, int fieldOrdinal) {
	        PipeWriter.presumeWriteFragment(output, MSG_CHANGESTATE_70);
	        PipeWriter.writeInt(output,MSG_CHANGESTATE_70_FIELD_ORDINAL_7, fieldOrdinal);
	        PipeWriter.publishWrites(output);
	}

}
