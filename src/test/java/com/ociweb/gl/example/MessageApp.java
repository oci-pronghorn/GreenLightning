package com.ociweb.gl.example;

import com.ociweb.gl.api.Builder;
import com.ociweb.gl.api.GreenApp;
import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.MessageReader;
import com.ociweb.gl.api.MsgRuntime;
import com.ociweb.gl.api.PubSubListener;
import com.ociweb.gl.api.PubSubStructuredWritable;
import com.ociweb.gl.api.PubSubStructuredWriter;
import com.ociweb.gl.api.StartupListener;
import com.ociweb.gl.impl.pubField.BytesFieldProcessor;
import com.ociweb.gl.impl.pubField.DecimalFieldProcessor;
import com.ociweb.gl.impl.pubField.IntegerFieldProcessor;
import com.ociweb.gl.impl.pubField.MessageConsumer;
import com.ociweb.gl.impl.pubField.RationalFieldProcessor;
import com.ociweb.gl.impl.pubField.UTF8FieldProcessor;
import com.ociweb.pronghorn.util.Appendables;

public class MessageApp implements GreenApp {
	
	//TODO: convert this into a unit test.
	public static void main( String[] args ) {
        GreenRuntime.run(new MessageApp());
    }
	

	@Override
	public void declareConfiguration(Builder builder) {
		
		// never started shutdown
		builder.limitThreads();
		
		
		//builder.enableTelemetry(true);
	}

	long globalValue = 100;
	long rationalValueNumerator   = 100;
	long rationalValueDenominator = 1;
	byte decimalE = -3;
	long decimalM = 123450000L;
	StringBuilder text1 = new StringBuilder();
	StringBuilder text2 = new StringBuilder();
	
	IntegerFieldProcessor intProc = new IntegerFieldProcessor() {		
		@Override
		public boolean process(long value) {					
			globalValue = value-1;
			//System.out.append("fifth").println(globalValue);
			return globalValue>0;
		}		
	};

	RationalFieldProcessor rationalProc = new RationalFieldProcessor() {		
		@Override
		public boolean process(long numerator, long denominator) {					
			rationalValueNumerator = numerator-1;
			rationalValueDenominator = denominator+1;
			
			//System.out.append("second").println(rationalValueNumerator+"/"+rationalValueDenominator);
			
			return true;
		}		
	};
	
	DecimalFieldProcessor decimalProc = new DecimalFieldProcessor() {		
		@Override
		public boolean process(byte e, long m) {
			
			decimalE = e;
			decimalM = m-1L;
			
			//Appendables.appendDecimalValue(System.out.append("first"), decimalM, decimalE).append('\n');
			
			return true;
		}		
	};
	
	BytesFieldProcessor byteProc = new BytesFieldProcessor() {		
		@Override
		public boolean process(byte[] backing, int position, int length, int mask) {
			
			text1.setLength(0);
			Appendables.appendUTF8(text1, backing, position, length, mask);
			text1.append('A'+(0x0F & globalValue));
			
			int maxLen = 37;
			if (text1.length() > maxLen) {				
				String x = text1.substring(text1.length()-maxLen, text1.length()).toString();
				text1.setLength(0);
				text1.append(x);				
			}
			//System.out.println("third");
			return true;
		}		
	};
	
	UTF8FieldProcessor<StringBuilder> utf8Proc = new UTF8FieldProcessor<StringBuilder>() {		
		
		public boolean process(StringBuilder target ) {
			
			text2 = target;
			text2.append('A'+(0x0F&rationalValueDenominator));
			
			int maxLen = 13;
			if (text2.length() > maxLen) {				
				String x = text2.substring(text2.length()-maxLen, text2.length()).toString();
				text2.setLength(0);
				text2.append(x);				
			}
			//System.out.println("fourth");
			
			return true;
		}		
	};
	
	MessageConsumer consumer = new MessageConsumer()			
			.decimalProcessor(5, decimalProc)
			.rationalProcessor(3, rationalProc)
			.bytesProcessor(7, byteProc)
			.utf8Processor(8, utf8Proc, text2)
			.decimalProcessor(5, decimalProc)
			.bytesProcessor(7, byteProc) 
			.integerProcessor(1, intProc);

	PubSubStructuredWritable writable = new PubSubStructuredWritable() {
		
		@Override
		public void write(PubSubStructuredWriter writer) {
			
			writer.writeRational(3, rationalValueNumerator, rationalValueDenominator);
			writer.writeDecimal(5, decimalE, decimalM);
			writer.writeUTF8(8, text2);
			writer.writeLong(1, globalValue);
			writer.writeUTF8(7, text1);
			
		}		
	};
	
	boolean shutdown = false;
	
	@Override
	public void declareBehavior(final GreenRuntime runtime) {
		

		
		final GreenCommandChannel gccA = runtime.newCommandChannel(DYNAMIC_MESSAGING);
		PubSubListener listenerA = new PubSubListener() {

			@Override
			public boolean message(CharSequence topic, MessageReader payload) {
				
				//if (gccA.copyStructuredTopic(topic, payload, consumer)) {
				//	//did copy
				//}
				
				 if (!shutdown && consumer.process(payload)) {
				    	return gccA.publishStructuredTopic("B", writable);
				    } else {
				    	if (!shutdown) {
				    		shutdown = true;
				    		System.err.println("MessageApp done");
				    		runtime.shutdownRuntime();
				    	}
				    	return true;
				    }
			};
		
		};
		runtime.addPubSubListener(listenerA ).addSubscription("A");
		
		
		final GreenCommandChannel gccB = runtime.newCommandChannel(DYNAMIC_MESSAGING);
		PubSubListener listenerB = new PubSubListener() {

			@Override
			public boolean message(CharSequence topic, MessageReader payload) {
				 if (!shutdown && consumer.process(payload)) {
				    	return gccB.publishStructuredTopic("A", writable);
				    } else {
				    	if (!shutdown) {
				    		shutdown = true;
				    		System.err.println("done");
				    		runtime.shutdownRuntime();
				    	}
				    	return true;
				    }
			}
			
		};
		runtime.addPubSubListener(listenerB ).addSubscription("B");
		
		final GreenCommandChannel gccC = runtime.newCommandChannel(DYNAMIC_MESSAGING);
		StartupListener startupListener = new StartupListener() {
			@Override
			public void startup() {
				gccC.presumePublishStructuredTopic("A", writable);
			}			
		};
		runtime.addStartupListener(startupListener);
		
	}

}
