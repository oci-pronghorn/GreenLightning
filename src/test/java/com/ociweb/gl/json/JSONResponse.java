package com.ociweb.gl.json;

import com.ociweb.json.JSONExtractor;
import com.ociweb.json.JSONExtractorCompleted;
import com.ociweb.json.JSONType;
import com.ociweb.json.appendable.AppendableByteWriter;
import com.ociweb.json.encode.JSONRenderer;
import com.ociweb.pronghorn.pipe.ChannelReader;
import com.ociweb.pronghorn.util.parse.JSONReader;

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

    public static final JSONExtractorCompleted jsonExtractor = new JSONExtractor()
            .newPath(JSONType.TypeInteger).key("status").completePath("status")
            .newPath(JSONType.TypeString).key("message").completePath("message")
            .newPath(JSONType.TypeString).key("body").completePath("body");

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

    public static JSONReader createReader() {
        return jsonExtractor.reader();
    }

    public boolean readFromJSON(JSONReader jsonReader, ChannelReader reader) {
        jsonReader.clear();
        status = (int)jsonReader.getLong("status".getBytes(), reader);
        jsonReader.getText("message".getBytes(), reader, message);
        jsonReader.getText("body".getBytes(), reader, body);
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
