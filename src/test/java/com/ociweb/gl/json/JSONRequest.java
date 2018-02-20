package com.ociweb.gl.json;

import com.ociweb.gl.api.HTTPRequestReader;
import com.ociweb.json.JSONExtractor;
import com.ociweb.json.JSONExtractorCompleted;
import com.ociweb.json.JSONType;
import com.ociweb.json.encode.JSONRenderer;
import com.ociweb.pronghorn.pipe.ChannelReader;
import com.ociweb.pronghorn.util.parse.JSONReader;

public class JSONRequest {
    private final StringBuilder id1 = new StringBuilder();
    private final StringBuilder id2 = new StringBuilder();
    private long timestamp = 567891345;
    private int value;

    private StringBuilder extractTemp = new StringBuilder();

    public static final JSONRenderer<JSONRequest> renderer = new JSONRenderer<JSONRequest>()
            .beginObject()
            .string("ID1", o -> o.id1)
            .string("ID2", o -> o.id2)
            .string("TimeStamp", o -> "1969-12-31 18:02:03.0456 CST")
            .integer("Value", o -> o.value)
            .endObject();

    public static final JSONExtractorCompleted jsonExtractor = new JSONExtractor()
            .newPath(JSONType.TypeString).key("ID1").completePath("ID1")
            .newPath(JSONType.TypeString).key("ID2").completePath("ID2")
            .newPath(JSONType.TypeString).key("TimeStamp").completePath("TimeStamp")
            .newPath(JSONType.TypeInteger).key("Value").completePath("Value");

    public JSONRequest() {
        this.timestamp = -1;
        this.value = -1;
    }

    public void reset() {
        id1.setLength(0);
        id2.setLength(0);
        this.timestamp = -1;
        this.value = -1;
        this.extractTemp.setLength(0);
    }

    public String getId1() {
        return id1.toString();
    }

    public String getId2() {
        return id2.toString();
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getValue() {
        return value;
    }

    public static JSONReader createReader() {
        return jsonExtractor.reader();
    }

    public boolean readFromJSON(JSONReader jsonReader, ChannelReader channelReader) {
        jsonReader.getText("ID1".getBytes(), channelReader, id1);
        jsonReader.getText("ID2".getBytes(), channelReader, id2);
        jsonReader.getText("TimeStamp".getBytes(), channelReader, extractTemp);
        value = (int) jsonReader.getLong("Value".getBytes(), channelReader);
        return true;
    }

    public void setId1(String id1) {
        this.id1.append(id1);
    }

    public void setId2(String id2) {
        this.id2.append(id2);
    }

    public void setValue(int value) {
        this.value = value;
    }
}