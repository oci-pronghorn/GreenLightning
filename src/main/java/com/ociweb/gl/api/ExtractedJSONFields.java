package com.ociweb.gl.api;

import com.ociweb.json.JSONAccumRule;
import com.ociweb.pronghorn.network.http.CompositeRoute;

public interface ExtractedJSONFields {

    public <T extends Enum<T>> ExtractedJSONFields integerField(String extractionPath, T field);    
    public <T extends Enum<T>> ExtractedJSONFields stringField(String extractionPath, T field);    
    public <T extends Enum<T>> ExtractedJSONFields decimalField(String extractionPath, T field);    
    public <T extends Enum<T>> ExtractedJSONFields booleanField(String extractionPath, T field);    
    public <T extends Enum<T>> ExtractedJSONFields integerField(boolean isAligned, JSONAccumRule accumRule, String extractionPath, T field);
    public <T extends Enum<T>> ExtractedJSONFields stringField(boolean isAligned, JSONAccumRule accumRule, String extractionPath, T field);
    public <T extends Enum<T>> ExtractedJSONFields decimalField(boolean isAligned, JSONAccumRule accumRule, String extractionPath, T field);
    public <T extends Enum<T>> ExtractedJSONFields booleanField(boolean isAligned, JSONAccumRule accumRule, String extractionPath, T field);

    CompositeRoute path(CharSequence path);
}
