package com.ociweb.gl.json;

import com.ociweb.json.JSONExtractor;
import com.ociweb.json.JSONExtractorCompleted;
import com.ociweb.json.JSONType;
import com.ociweb.json.encode.JSONRenderer;
import com.ociweb.pronghorn.pipe.ChannelReader;
import com.ociweb.pronghorn.util.AppendableByteWriter;

public class JSONResponse {
    private int status = 0;
    private final StringBuilder message = new StringBuilder();
    private final StringBuilder body = new StringBuilder();

    private static final JSONRenderer<JSONResponse> jsonRenderer = new JSONRenderer<JSONResponse>()
            .beginObject()
            .integer("status", o->o.status)
            .string("message", o->o.message)
            .string("body", o->o.body)
            .endObject();

    public enum Fields {
    	Status, Message, Body;
    }    
    
    public static final JSONExtractorCompleted jsonExtractor = new JSONExtractor()
            .newPath(JSONType.TypeInteger).key("status").completePath("status",Fields.Status)
            .newPath(JSONType.TypeString).key("message").completePath("message",Fields.Message)
            .newPath(JSONType.TypeString).key("body").completePath("body",Fields.Body);

    public void reset() {
        status = 0;
        message.setLength(0);
        this.message.setLength(0);
        body.setLength(0);
    }

    public void setStatusMessage(StatusMessages statusMessage) {
        this.status = statusMessage.getStatusCode();
        this.message.append(statusMessage.getStatusMessage());
    }

    public int getStatus() { return status; }

    public String getMessage() {
        return message.toString();
    }

    public String getBody() {
        return body.toString();
    }

    public void setBody(String body) {
        this.body.append(body);
    }

    public boolean readFromJSON(ChannelReader reader) {
       
        status = (int)reader.structured().readLong(Fields.Status);
        reader.structured().readText(Fields.Message, message);
        reader.structured().readText(Fields.Body, body);
        
        return true;
    }

    public void writeToJSON(AppendableByteWriter writer) {
        jsonRenderer.render(writer, this);
    }

    public enum StatusMessages {
        SUCCESS(200, "Success"),
        FAILURE(500, "Server Error"),
        BAD_REQUEST(400, "Bad Request");

        private final int statusCode;
        private final String statusMessage;

        StatusMessages(int statusCode, String statusMessage) {
            this.statusCode = statusCode;
            this.statusMessage = statusMessage;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getStatusMessage() {
            return statusMessage;
        }
    }
}
