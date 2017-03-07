package com.ociweb.gl.impl.schema;

import com.ociweb.pronghorn.pipe.FieldReferenceOffsetManager;
import com.ociweb.pronghorn.pipe.MessageSchema;
public class MessageSubscription extends MessageSchema {


	public final static FieldReferenceOffsetManager FROM = new FieldReferenceOffsetManager(
		    new int[]{0xc0400003,0xa8000000,0xb8000001,0xc0200003,0xc0400003,0x80000000,0x80000001,0xc0200003},
		    (short)0,
		    new String[]{"Publish","Topic","Payload",null,"StateChanged","OldOrdinal","NewOrdinal",null},
		    new long[]{103, 1, 3, 0, 71, 8, 9, 0},
		    new String[]{"global",null,null,null,"global",null,null,null},
		    "MessageSubscriber.xml",
		    new long[]{2, 2, 0},
		    new int[]{2, 2, 0});
    
	public static final int MSG_PUBLISH_103 = 0x00000000;
	public static final int MSG_PUBLISH_103_FIELD_TOPIC_1 = 0x01400001;
	public static final int MSG_PUBLISH_103_FIELD_PAYLOAD_3 = 0x01C00003;
	public static final int MSG_STATECHANGED_71 = 0x00000004;
	public static final int MSG_STATECHANGED_71_FIELD_OLDORDINAL_8 = 0x00000001;
	public static final int MSG_STATECHANGED_71_FIELD_NEWORDINAL_9 = 0x00000002;
    
    public static final MessageSubscription instance = new MessageSubscription();
    
    private MessageSubscription() {
        super(FROM);
    }
        
}
