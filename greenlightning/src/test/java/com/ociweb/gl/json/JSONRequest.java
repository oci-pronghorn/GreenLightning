package com.ociweb.gl.json;

import com.ociweb.gl.api.HTTPRequestReader;
import com.ociweb.json.JSONExtractorImpl;
import com.ociweb.json.JSONExtractorCompleted;
import com.ociweb.json.JSONType;
import com.ociweb.json.encode.JSONRenderer;
import com.ociweb.pronghorn.pipe.ChannelReader;

public class JSONRequest {
    private final StringBuilder id1 = new StringBuilder();

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("JSONRequest{");
        sb.append("id1=").append(id1);
        sb.append(", id2=").append(id2);
        sb.append(", timestamp=").append(timestamp);
        sb.append(", value=").append(value);
        sb.append('}');
        return sb.toString();
    }

    private final StringBuilder id2 = new StringBuilder();
    private long timestamp = 567891345;
    private int value;

    private StringBuilder extractTemp = new StringBuilder();

    public static final JSONRenderer<JSONRequest> renderer = new JSONRenderer<JSONRequest>()
            .beginObject()
            .string("ID1", (o,t) -> t.append(o.id1))
            .string("ID2", (o,t) -> t.append(o.id2))
            .string("TimeStamp", (o,t) -> t.append("1969-12-31 18:02:03.0456 CST"))
            .integer("Value", o -> o.value)
            .endObject();

    public enum Fields {
    	ID1, ID2, TimeStamp, Value;
    }
    
    public static final JSONExtractorCompleted jsonExtractor = new JSONExtractorImpl()
            .newPath(JSONType.TypeString).completePath("ID1","ID1",Fields.ID1)
            .newPath(JSONType.TypeString).completePath("ID2","ID2",Fields.ID2)
            .newPath(JSONType.TypeString).completePath("TimeStamp", "TimeStamp",Fields.TimeStamp)
            .newPath(JSONType.TypeInteger).completePath("Value","Value",Fields.Value);

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

    public boolean readFromJSON(ChannelReader channelReader) {    	
    	channelReader.structured().readText(Fields.ID1, id1);
    	channelReader.structured().readText(Fields.ID2, id2);
    	channelReader.structured().readText(Fields.TimeStamp, extractTemp);
        value = (int) channelReader.structured().readLong(Fields.Value);
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