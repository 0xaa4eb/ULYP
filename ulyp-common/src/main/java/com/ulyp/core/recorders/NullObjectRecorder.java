package com.ulyp.core.recorders;

import com.ulyp.core.ByIdTypeResolver;
import com.ulyp.core.Type;
import com.ulyp.core.TypeResolver;
import com.ulyp.core.bytes.BytesIn;
import com.ulyp.core.bytes.BytesOut;
import org.jetbrains.annotations.NotNull;

public class NullObjectRecorder extends ObjectRecorder {

    protected NullObjectRecorder(byte id) {
        super(id);
    }

    @Override
    public boolean supports(Class<?> type) {
        return false;
    }

    @Override
    public boolean supportsAsyncRecording() {
        return true;
    }

    @Override
    public ObjectRecord read(@NotNull Type objectType, BytesIn input, ByIdTypeResolver typeResolver) {
        // still need to read as this recorder may be used inside another recorder
        input.readBoolean();
        return NullObjectRecord.getInstance();
    }

    @Override
    public void write(Object object, BytesOut out, TypeResolver typeResolver) throws Exception {
        out.write(false);
    }
}
