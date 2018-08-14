package com.ociweb.gl.api;

import com.ociweb.json.JSONAccumRule;
import com.ociweb.json.JSONAligned;
import com.ociweb.json.JSONRequired;
import com.ociweb.pronghorn.struct.ByteSequenceValidator;
import com.ociweb.pronghorn.struct.DecimalValidator;
import com.ociweb.pronghorn.struct.LongValidator;

public interface ExtractedJSONFields<E extends ExtractedJSONFields<?>> {
	
    public <T extends Enum<T>> E integerField(String extractionPath, T field);    
    public <T extends Enum<T>> E stringField(String extractionPath, T field);    
    public <T extends Enum<T>> E decimalField(String extractionPath, T field);    
    public <T extends Enum<T>> E booleanField(String extractionPath, T field);   
    
    public <T extends Enum<T>> E integerField(JSONAligned isAligned, JSONAccumRule accumRule, String extractionPath, T field);
    public <T extends Enum<T>> E stringField(JSONAligned isAligned,  JSONAccumRule accumRule,  String extractionPath, T field);
    public <T extends Enum<T>> E decimalField(JSONAligned isAligned, JSONAccumRule accumRule, String extractionPath, T field);
    public <T extends Enum<T>> E booleanField(JSONAligned isAligned, JSONAccumRule accumRule, String extractionPath, T field);
    
    public <T extends Enum<T>> E integerField(String extractionPath, T field, JSONRequired isRequired, LongValidator validator);    
    public <T extends Enum<T>> E stringField(String extractionPath, T field, JSONRequired isRequired, ByteSequenceValidator validator);    
    public <T extends Enum<T>> E decimalField(String extractionPath, T field, JSONRequired isRequired, DecimalValidator validator);    
    public <T extends Enum<T>> E booleanField(String extractionPath, T field, JSONRequired isRequired); 
    
    public <T extends Enum<T>> E integerField(JSONAligned isAligned, JSONAccumRule accumRule, String extractionPath, T field, JSONRequired isRequired, LongValidator validator);
    public <T extends Enum<T>> E stringField(JSONAligned isAligned, JSONAccumRule accumRule, String extractionPath, T field, JSONRequired isRequired, ByteSequenceValidator validator);
    public <T extends Enum<T>> E decimalField(JSONAligned isAligned, JSONAccumRule accumRule, String extractionPath, T field, JSONRequired isRequired, DecimalValidator validator);
    public <T extends Enum<T>> E booleanField(JSONAligned isAligned, JSONAccumRule accumRule, String extractionPath, T field, JSONRequired isRequired);
        
}
