package com.ociweb.gl.impl;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.ociweb.gl.impl.schema.IngressMessages;
import com.ociweb.gl.impl.schema.MessagePrivate;
import com.ociweb.gl.impl.schema.MessagePubSub;
import com.ociweb.gl.impl.schema.MessageSubscription;
import com.ociweb.gl.impl.schema.TrafficAckSchema;
import com.ociweb.gl.impl.schema.TrafficOrderSchema;
import com.ociweb.gl.impl.schema.TrafficReleaseSchema;
import com.ociweb.pronghorn.pipe.util.build.FROMValidation;

public class SchemaValidationTest {

    @Test
    public void messagePubSubFROMTest() {
        assertTrue(FROMValidation.checkSchema("/MessagePubSub.xml", MessagePubSub.class));
    }

    @Test
    public void messagePrivateTest() {
        assertTrue(FROMValidation.checkSchema("/MessagePrivate.xml", MessagePrivate.class));
    }
    
    @Test
    public void messageSubscriptionFROMTest() {
        assertTrue(FROMValidation.checkSchema("/MessageSubscriber.xml", MessageSubscription.class));
    }   

    @Test
    public void trafficAckFROMTest() {
        assertTrue(FROMValidation.checkSchema("/TrafficAckSchema.xml", TrafficAckSchema.class));
    }
    
    @Test
    public void trafficOrderFROMTest() {
        assertTrue(FROMValidation.checkSchema("/TrafficOrderSchema.xml", TrafficOrderSchema.class));
    }
        
    @Test
    public void trafficReleaseFROMTest() {
        assertTrue(FROMValidation.checkSchema("/TrafficReleaseSchema.xml", TrafficReleaseSchema.class));
    }
    
	@Test
	public void ingestMessagesFROMTest() {
	    assertTrue(FROMValidation.checkSchema("/IngressMessages.xml", IngressMessages.class));
	}
    
    
    
}
