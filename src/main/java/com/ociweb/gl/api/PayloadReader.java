package com.ociweb.gl.api;

import com.ociweb.pronghorn.pipe.DataInputBlobReader;
import com.ociweb.pronghorn.pipe.Pipe;

@SuppressWarnings("rawtypes")
public class PayloadReader extends DataInputBlobReader{

    public PayloadReader(Pipe pipe) {
        super(pipe);
    }
    
    
    void setPattern(byte[] dataPattern) {
    	
    	//TODO: keeping this data pattern will alow for methods like    pr.readAsDouble(ordinalPos)
    	//TODO: cache indexes as we go...
    	
    }
    
    //TODO: requires support for mark
    //TODO: as with PayloadWriter needs a bounds check for the makers
    
    
    

}
