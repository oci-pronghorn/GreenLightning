package com.ociweb.gl.example;

import com.ociweb.gl.api.Builder;
import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.MsgRuntime;
import com.ociweb.gl.api.GreenApp;
import com.ociweb.gl.api.PubSubStructuredWritable;
import com.ociweb.gl.api.PubSubStructuredWriter;
import com.ociweb.gl.impl.pubField.BytesFieldProcessor;
import com.ociweb.gl.impl.pubField.DecimalFieldProcessor;
import com.ociweb.gl.impl.pubField.IntegerFieldProcessor;
import com.ociweb.gl.impl.pubField.MessageConsumer;
import com.ociweb.gl.impl.pubField.RationalFieldProcessor;
import com.ociweb.gl.impl.pubField.UTF8FieldProcessor;
import com.ociweb.pronghorn.util.Appendables;
import com.ociweb.pronghorn.util.math.Decimal;

public class MessageApp implements GreenApp {
	
	public static void main( String[] args ) {
        MsgRuntime.run(new MessageApp());
    }
			
	@Override
	public void declareConfiguration(Builder builder) {
		
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
			System.out.println(globalValue);
			return globalValue>0;
		}		
	};

	RationalFieldProcessor rationalProc = new RationalFieldProcessor() {		
		@Override
		public boolean process(long numerator, long denominator) {					
			rationalValueNumerator = numerator-1;
			rationalValueDenominator = denominator+1;
			System.out.println(rationalValueNumerator+"/"+rationalValueDenominator);
			return globalValue>0;
		}		
	};
	
	DecimalFieldProcessor decimalProc = new DecimalFieldProcessor() {		
		@Override
		public boolean process(byte e, long m) {
			
			decimalE = e;
			decimalM = m-1L;
			
			Appendables.appendDecimalValue(System.out, decimalM, decimalE);
			
			return true;
		}		
	};
	
	BytesFieldProcessor byteProc = new BytesFieldProcessor() {		
		@Override
		public boolean process(byte[] backing, int position, int length, int mask) {
			
			text1.setLength(0);
			Appendables.appendUTF8(text1, backing, position, length, mask);
			text1.append('A'+(0x0F & text1.length()));
			System.out.println("1 "+text1);
			
			return true;
		}		
	};
	
	UTF8FieldProcessor<StringBuilder> utf8Proc = new UTF8FieldProcessor<StringBuilder>() {		
		
		public boolean process(StringBuilder target ) {
			
			text2 = target;
			text2.append('A'+(0x0F&text2.length()));
			System.out.println("2 "+text2);
			
			return true;
		}		
	};
	
	MessageConsumer consumer = new MessageConsumer()			
			.decimalProcessor(5, decimalProc)
			.rationalProcessor(3, rationalProc)
		//	.bytesProcessor(7, byteProc)
		//	.utf8Processor(8, utf8Proc, text2)
			.integerProcessor(1, intProc);

	PubSubStructuredWritable writable = new PubSubStructuredWritable() {
		
		@Override
		public void write(PubSubStructuredWriter writer) {
			
			writer.writeDecimal(5, decimalE, decimalM);
			writer.writeLong(1, globalValue);
			writer.writeRational(3, rationalValueNumerator, rationalValueDenominator);
		//	writer.writeUTF8(7, text1);
		//	writer.writeUTF8(8, text2);
			
		}		
	};
	
	@Override
	public void declareBehavior(final MsgRuntime runtime) {
		
		final GreenCommandChannel gccA = runtime.newCommandChannel(DYNAMIC_MESSAGING);
		runtime.addPubSubListener((topic, payload)->{

		    if (consumer.process(payload)) {
		    	return gccA.publishStructuredTopic("B", writable);
		    } else {
		    	runtime.shutdownRuntime();
		    	return true;
		    }
		}).addSubscription("A");
		
		
		final GreenCommandChannel gccB = runtime.newCommandChannel(DYNAMIC_MESSAGING);
		runtime.addPubSubListener((topic, payload)->{

		    if (consumer.process(payload)) {
		    	return gccB.publishStructuredTopic("A", writable);
		    } else {
		    	runtime.shutdownRuntime();
		    	return true;
		    }
		}).addSubscription("B");
		
		final GreenCommandChannel gccC = runtime.newCommandChannel(DYNAMIC_MESSAGING);
		runtime.addStartupListener(()->{			
			gccC.presumePublishStructuredTopic("A", writable);
		} 
		);
		
	}

}
