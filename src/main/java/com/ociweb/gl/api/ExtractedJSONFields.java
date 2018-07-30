package com.ociweb.gl.api;

import com.ociweb.json.JSONAccumRule;

public interface ExtractedJSONFields<E extends ExtractedJSONFields> {
	
	//NOTE: new feature to add.
	//TODO: add validation follow on methods, check for set of strings or length or int value range..

    public <T extends Enum<T>> E integerField(String extractionPath, T field);    
    public <T extends Enum<T>> E stringField(String extractionPath, T field);    
    public <T extends Enum<T>> E decimalField(String extractionPath, T field);    
    public <T extends Enum<T>> E booleanField(String extractionPath, T field);    
    public <T extends Enum<T>> E integerField(boolean isAligned, JSONAccumRule accumRule, String extractionPath, T field);
    public <T extends Enum<T>> E stringField(boolean isAligned, JSONAccumRule accumRule, String extractionPath, T field);
    public <T extends Enum<T>> E decimalField(boolean isAligned, JSONAccumRule accumRule, String extractionPath, T field);
    public <T extends Enum<T>> E booleanField(boolean isAligned, JSONAccumRule accumRule, String extractionPath, T field);

}
