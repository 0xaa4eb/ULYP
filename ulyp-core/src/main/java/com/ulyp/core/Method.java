package com.ulyp.core;

import com.ulyp.core.printers.ObjectBinaryRecorder;
import com.ulyp.transport.*;
import lombok.Builder;
import lombok.ToString;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

@Builder
@ToString
public class Method {

    private final long id;
    private final String name;
    private final Type declaringType;
    private final boolean isStatic;
    private final boolean isConstructor;
    private final boolean returnsSomething;
    private final ObjectBinaryRecorder[] paramPrinters;
    private final ObjectBinaryRecorder returnValuePrinter;

    // If was dumped to the output file
    @Builder.Default
    private volatile boolean writtenToFile = false;

    public boolean wasWrittenToFile() {
        return writtenToFile;
    }

    public void setWrittenToFile() {
        writtenToFile = true;
    }

    public long getId() {
        return id;
    }

    public boolean isStatic() {
        return isStatic;
    }

    public boolean isConstructor() {
        return isConstructor;
    }

    public ObjectBinaryRecorder[] getParamPrinters() {
        if (paramPrinters == null) {

        }
        return paramPrinters;
    }

    public ObjectBinaryRecorder getReturnValuePrinter() {
        return returnValuePrinter;
    }

    public Type getDeclaringType() {
        return declaringType;
    }

    public String getName() {
        return name;
    }

    public boolean returnsSomething() {
        return returnsSomething;
    }

    public String toShortString() {
        return declaringType.getName() + "." + name;
    }

    public void serialize(BinaryMethodEncoder encoder) {
        encoder.id(this.id);
        encoder.returnsSomething(this.returnsSomething ? BooleanType.T : BooleanType.F);
        encoder.staticFlag(this.isStatic ? BooleanType.T : BooleanType.F);
        encoder.constructor(this.isConstructor ? BooleanType.T : BooleanType.F);
        BinaryTypeEncoder binaryTypeEncoder = new BinaryTypeEncoder();

        encoder.name(this.name);

        // TODO move to some utils class
        MutableDirectBuffer wrappedBuffer = encoder.buffer();
        int headerLength = 4;
        int limit = encoder.limit();

        binaryTypeEncoder.wrap(wrappedBuffer, limit + headerLength);
        declaringType.serialize(binaryTypeEncoder);

        int typeSerializedLength = binaryTypeEncoder.encodedLength();

        encoder.limit(limit + headerLength + typeSerializedLength);
        wrappedBuffer.putInt(limit, typeSerializedLength, java.nio.ByteOrder.LITTLE_ENDIAN);
    }

    public static Method deserialize(BinaryMethodDecoder decoder) {

        String name = decoder.name();

        UnsafeBuffer buffer = new UnsafeBuffer();
        decoder.wrapDeclaringTypeValue(buffer);
        BinaryTypeDecoder typeDecoder = new BinaryTypeDecoder();
        typeDecoder.wrap(buffer, 0, BinaryTypeEncoder.BLOCK_LENGTH, 0);
        Type declaringType = Type.deserialize(typeDecoder);

        return Method.builder()
                .id(decoder.id())
                .name(name)
                .declaringType(declaringType)
                .isStatic(decoder.staticFlag() == BooleanType.T)
                .isConstructor(decoder.constructor() == BooleanType.T)
                .returnsSomething(decoder.returnsSomething() == BooleanType.T)
                .build();
    }
}
