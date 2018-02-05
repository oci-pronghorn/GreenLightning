package com.ociweb.gl.example.prongstruct;

import com.ociweb.gl.structure.GreenRational;
import com.ociweb.pronghorn.structure.annotations.ProngProperty;
import com.ociweb.pronghorn.structure.annotations.ProngStruct;
import com.ociweb.pronghorn.structure.annotations.ProngStructFormatter;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

class SuperDuper {
    public SuperDuper() {
    }

    public SuperDuper(SuperDuper rhs) {
    }

    @Override
    public int hashCode() {
        return 0;
    }

    public void clear() {
    }

    public void assignFrom(SuperDuper rhs) {
    }

    public void toStringProperties(ProngStructFormatter sb) {

    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || !(o instanceof SuperDuper)) return false;
        return true;
    }

    public void readExternal(ObjectInput input) throws IOException, ClassNotFoundException {
    }

    public void writeExternal(ObjectOutput output) throws IOException {
    }
}

@ProngStruct
public abstract class Big extends SuperDuper {

    public Big() {
    }

    public Big(Big rhs) {
        super(rhs);
    }

    // Primitives
    abstract boolean isBooleanNull();
    @ProngProperty(nullable=true)
    abstract boolean getBoolean();
    @ProngProperty(nullable=true)
    abstract void setBoolean(boolean value);
    @ProngProperty(nullable=true)
    abstract byte getByte();
    abstract void setByte(byte value);
    abstract boolean isByteNull();
    abstract short getShort();
    @ProngProperty(nullable=true)
    abstract void setShort(short value);
    abstract boolean isShortNull();
    abstract int getInt();
    abstract void setInt(int value);
    abstract long getLong();
    abstract void setLong(long value);
    abstract char getChar();
    abstract void setChar(char value);
    abstract float getFloat();
    abstract void setFloat(float value);
    abstract double getDouble();
    abstract void setDouble(double value);

    // Boxed
    @ProngProperty(nullable=true)
    abstract Boolean getOBoolean();
    @ProngProperty(nullable=true)
    abstract void setOBoolean(Boolean value);
    @ProngProperty(nullable=true)
    abstract Byte getOByte();
    @ProngProperty(nullable=true)
    abstract void setOByte(Byte value);
    @ProngProperty(nullable=true)
    abstract Short getOShort();
    @ProngProperty(nullable=true)
    abstract void setOShort(Short value);
    abstract Integer getOInt();
    @ProngProperty(nullable=true)
    abstract void setOInt(Integer value);
    @ProngProperty(nullable=true)
    abstract Long getOLong();
    @ProngProperty(nullable=true)
    abstract void setOLong(Long value);
    abstract Character getOChar();
    abstract void setOChar(Character value);
    abstract Float getOFloat();
    @ProngProperty(nullable=true)
    abstract void setOFloat(Float value);
    @ProngProperty(nullable=true)
    abstract Double getODouble();
    abstract void setODouble(Double value);

    // String
    abstract CharSequence getStringCC();
    abstract void setStringCC(CharSequence value);
    @ProngProperty(nullable=true)
    abstract CharSequence getStringNC();
    abstract void setStringNC(CharSequence value);
    abstract CharSequence getStringCN();
    @ProngProperty(nullable=true)
    abstract void setStringCN(CharSequence value);
    @ProngProperty(nullable=true)
    abstract CharSequence getStringNN();
    @ProngProperty(nullable=true)
    abstract void setStringNN(CharSequence value);

    // Structs using null references

    abstract GreenRational getGreenRationalCCR();
    abstract void setGreenRationalCCR(GreenRational value);

    @ProngProperty(nullable=true)
    abstract GreenRational getGreenRationalNCR();
    abstract void setGreenRationalNCR(GreenRational value);

    abstract GreenRational getGreenRationalCNR();
    @ProngProperty(nullable=true)
    abstract void setGreenRationalCNR(GreenRational value);

    @ProngProperty(nullable=true)
    abstract GreenRational getGreenRationalNNR();
    @ProngProperty(nullable=true)
    abstract void setGreenRationalNNR(GreenRational value);

    // Structs using null flags

    @ProngProperty(nullable=true)
    abstract GreenRational getGreenRationalNCB();
    abstract void setGreenRationalNCB(GreenRational value);
    abstract boolean isGreenRationalNCBNull();

    abstract GreenRational getGreenRationalCNB();
    @ProngProperty(nullable=true)
    abstract void setGreenRationalCNB(GreenRational value);
    abstract boolean isGreenRationalCNBNull();

    @ProngProperty(nullable=true)
    abstract GreenRational getGreenRationalNNB();
    @ProngProperty(nullable=true)
    abstract void setGreenRationalNNB(GreenRational value);
    abstract boolean isGreenRationalNNBNull();

    // Structs without getters

    abstract void setBigXCR(GreenRational value);
    @ProngProperty(nullable=true)
    abstract void setBigXNR(GreenRational value);
    abstract void setBigXCB(GreenRational value);
    @ProngProperty(nullable=true)
    abstract void setBigXNB(GreenRational value);
    abstract boolean isBigXNBNull();

    // Structs without setters

    abstract GreenRational getBigCXR();
    @ProngProperty(nullable=true)
    abstract GreenRational getBigNXR();
    abstract GreenRational getBigCXB();
    @ProngProperty(nullable=true)
    abstract GreenRational getBigNXB();
    abstract boolean isBigNXBNull();
}
