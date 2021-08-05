package com.ulyp.core.printers;

import com.ulyp.core.ByIdTypeResolver;
import com.ulyp.core.Type;
import com.ulyp.core.TypeResolver;
import com.ulyp.core.printers.bytes.BinaryInput;
import com.ulyp.core.printers.bytes.BinaryOutput;

public class StringPrinter extends ObjectBinaryPrinter {

    protected StringPrinter(byte id) {
        super(id);
    }

    @Override
    boolean supports(Type type) {
        return type.isExactlyJavaLangString();
    }

    @Override
    public ObjectRepresentation read(Type objectType, BinaryInput input, ByIdTypeResolver typeResolver) {
        return new StringObjectRepresentation(objectType, input.readString());
    }

    @Override
    public void write(Object object, Type classDescription, BinaryOutput out, TypeResolver typeResolver) throws Exception {
        out.writeString((String) object);
    }
}
