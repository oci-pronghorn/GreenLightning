package com.ociweb.gl.structure;

import com.ociweb.pronghorn.structure.annotations.ProngStruct;

@ProngStruct
public abstract class GreenRational {
    public abstract long getNumerator();

    public abstract void setNumerator(long value);

    public abstract long getDenominator();

    public abstract void setDenominator(long value);

    public boolean isInfinity() {
        return getDenominator() == 0;
    }

    public final double getValue() {
        return (double) getNumerator() / (double) getDenominator();
    }

    public String toString() {
        return String.valueOf(getNumerator()) + "/" + getDenominator() + "=" + getValue();
    }

}
