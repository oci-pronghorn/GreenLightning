package com.ociweb.gl.json;

import com.ociweb.json.JSONExtractor;
import com.ociweb.json.JSONExtractorCompleted;
import com.ociweb.json.JSONType;
import com.ociweb.json.encode.JSONRenderer;

public class JSONRequest {
    public String name;
    public int age;

    static final JSONRenderer<JSONRequest> renderer = new JSONRenderer<JSONRequest>()
            .beginObject()
            .string("name", o->o.name)
            .integer("age", o->o.age)
            .endObject();

    public static final JSONExtractorCompleted jsonExtractor = new JSONExtractor()
            .newPath(JSONType.TypeString).key("name").completePath("name")
            .newPath(JSONType.TypeInteger).key("age").completePath("age");
}
