package com.ulyp.core.recorders.arrays;

import com.ulyp.core.ByIdTypeResolver;
import com.ulyp.core.Type;
import com.ulyp.core.TypeResolver;
import com.ulyp.core.recorders.IdentityObjectRecord;
import com.ulyp.core.recorders.ObjectRecord;
import com.ulyp.core.bytes.BytesIn;
import com.ulyp.core.bytes.BytesOut;
import com.ulyp.core.recorders.ObjectRecorder;
import com.ulyp.core.recorders.ObjectRecorderRegistry;

import org.jetbrains.annotations.NotNull;

public class ByteArrayRecorder extends ObjectRecorder {

    public ByteArrayRecorder(byte id) {
        super(id);
    }

    @Override
    public boolean supports(Class<?> type) {
        return type == byte[].class;
    }

    @Override
    public ObjectRecord read(@NotNull Type type, BytesIn input, ByIdTypeResolver typeResolver) {
        IdentityObjectRecord identityRecord = (IdentityObjectRecord) ObjectRecorderRegistry.IDENTITY_RECORDER.getInstance().read(type, input, typeResolver);
        return new ByteArrayRecord(type, identityRecord, input.readInt());
    }

    @Override
    public void write(Object object, BytesOut out, TypeResolver typeResolver) throws Exception {
        ObjectRecorderRegistry.IDENTITY_RECORDER.getInstance().write(object, out, typeResolver);
        byte[] array = (byte[]) object;
        out.write(array.length);
    }
}
