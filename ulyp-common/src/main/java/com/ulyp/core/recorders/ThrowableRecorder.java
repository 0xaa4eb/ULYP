package com.ulyp.core.recorders;

import com.ulyp.core.ByIdTypeResolver;
import com.ulyp.core.Type;
import com.ulyp.core.TypeResolver;
import com.ulyp.core.recorders.bytes.BinaryInput;
import com.ulyp.core.recorders.bytes.BinaryOutput;
import com.ulyp.core.recorders.bytes.BufferBinaryOutput;
import org.jetbrains.annotations.NotNull;

public class ThrowableRecorder extends ObjectRecorder {

    protected ThrowableRecorder(byte id) {
        super(id);
    }

    @Override
    public boolean supports(Class<?> type) {
        return Throwable.class.isAssignableFrom(type);
    }

    @Override
    public ObjectRecord read(@NotNull Type type, BinaryInput input, ByIdTypeResolver typeResolver) {
        return new ThrowableRecord(type, input.readObject(typeResolver));
    }

    @Override
    public void write(Object object, BinaryOutput out, TypeResolver typeResolver) throws Exception {
        try (BinaryOutput nested = out.nest()) {
            Throwable t = (Throwable) object;
            nested.append(t.getMessage(), typeResolver);
        }
    }
}
