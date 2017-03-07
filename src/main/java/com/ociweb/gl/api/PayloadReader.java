package com.ociweb.gl.api;

import com.ociweb.pronghorn.pipe.DataInputBlobReader;
import com.ociweb.pronghorn.pipe.Pipe;

@SuppressWarnings("rawtypes")
public class PayloadReader extends DataInputBlobReader{

    public PayloadReader(Pipe pipe) {
        super(pipe);
    }
    
    
    //TODO: requires support for mark
    //TODO: as with PayloadWriter needs a bounds check for the makers
    
    
    

}
