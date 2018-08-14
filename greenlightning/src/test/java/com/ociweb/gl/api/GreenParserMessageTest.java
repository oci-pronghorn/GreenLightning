package com.ociweb.gl.api;

import com.ociweb.pronghorn.pipe.ChannelReader;
import com.ociweb.pronghorn.pipe.DataOutputBlobWriter;
import org.junit.Test;

import java.text.DecimalFormat;
import java.text.NumberFormat;

import static org.junit.Assert.assertEquals;

enum FieldType {
    integer,
    string,
    floatingPoint,
    int64;

    String getPattern() {
        switch (this) {
            case integer:
                return "%u";
            case string:
                return "\"%b\"";
            case floatingPoint:
                return "%i%.";
            case int64:
                return "%u";
        }
        return null;
    }
}

class MsgField {
    final String key;
    final FieldType type;

    MsgField(String key, FieldType type) {
        this.key = key;
        this.type = type;
    }

    String getPattern() {
        return key + type.getPattern();
    }
}

public class GreenParserMessageTest {

    private final static String complexData = "st2sn1020pn\"NX-DCV-SM-BLU-2-V0-L0-S0-00\"cl637512101cc1pp36.3833pf\"N\"ld\"N\"in\"A\"fd61423765200000sd61426357200000";

    private static void complexStreamAppend(DataOutputBlobWriter<?> stream) {
        stream.append(complexData);
    }

    private static final MsgField[] messages = new MsgField[] {
            new MsgField("st", FieldType.integer),
            new MsgField("sn", FieldType.integer),
            new MsgField("cl", FieldType.integer),
            new MsgField("cc", FieldType.integer),
            new MsgField("pp", FieldType.floatingPoint),
            new MsgField("fd", FieldType.int64),
            new MsgField("sd", FieldType.int64),
            new MsgField("pf", FieldType.string),
            new MsgField("ld", FieldType.string),
            new MsgField("in", FieldType.string),
            new MsgField("pn", FieldType.string),
    };

    private static GreenTokenMap buildParser() {
        GreenTokenMap map = new GreenTokenMap();
        for (int i = 0; i < messages.length; i++) {
            map = map.add(i, messages[i].getPattern());
        }
        return map;
    }

    @Test
    public void complexStringTest() {
        NumberFormat formatter = new DecimalFormat("#0.0000");
        final GreenReader reader = buildParser().newReader();
        ChannelReader testToRead = BlobReaderFactory.generateExtractionDataToTest(new MyConsumer<DataOutputBlobWriter<?>>() {
            @Override
            public void accept(DataOutputBlobWriter<?> dataOutputBlobWriter) {
                complexStreamAppend(dataOutputBlobWriter);
            }
        });
        reader.beginRead(testToRead);
        StringBuilder rebuild = new StringBuilder();
        while (reader.hasMore()) {
            int parsedId = (int)reader.readToken();
            if (parsedId == -1) {
                reader.skipByte();
            }
            else {
                final MsgField msgField = messages[parsedId];
                final FieldType fieldType = msgField.type;
                final String key = msgField.key;
                rebuild.append(key);
                switch (fieldType) {
                    case integer: {
                        int value = (int) reader.extractedLong(0);
                        rebuild.append(value);
                        break;
                    }
                    case int64: {
                        long value = reader.extractedLong(0);
                        rebuild.append(value);
                        break;
                    }
                    case string: {
                        StringBuilder value = new StringBuilder();
                        reader.copyExtractedUTF8ToAppendable(0, value);
                        rebuild.append("\"");
                        rebuild.append(value);
                        rebuild.append("\"");
                        break;
                    }
                    case floatingPoint: {
                        double value = reader.extractedDouble(0);
                        rebuild.append(formatter.format(value));
                        break;
                    }
                }
            }
        }
        assertEquals(complexData, rebuild.toString());
    }
}
