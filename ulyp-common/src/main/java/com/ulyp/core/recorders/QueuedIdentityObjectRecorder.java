package com.ulyp.core.recorders;

import com.ulyp.core.ByIdTypeResolver;
import com.ulyp.core.Type;
import com.ulyp.core.TypeResolver;
import com.ulyp.core.bytes.BytesIn;
import com.ulyp.core.bytes.BytesOut;
import org.jetbrains.annotations.NotNull;

import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
public class QueuedIdentityObjectRecorder extends ObjectRecorder {

    protected QueuedIdentityObjectRecorder(byte id) {
        super(id);
    }

    @Override
    public boolean supports(Class<?> type) {
        return type == QueuedIdentityObject.class;
    }

    @Override
    public boolean supportsAsyncRecording() {
        return true;
    }

    @Override
    public ObjectRecord read(@NotNull Type objectType, BytesIn input, ByIdTypeResolver typeResolver) {
        int nestedTypeId = input.readInt();
        int identityHashCode = input.readInt();
        return new IdentityObjectRecord(typeResolver.getType(nestedTypeId), identityHashCode);
    }

    @Override
    public void write(Object object, BytesOut out, TypeResolver typeResolver) throws Exception {
        QueuedIdentityObject identityObject = (QueuedIdentityObject) object;
        out.write(identityObject.getTypeId());
        out.write(identityObject.getIdentityHashCode());
    }
}
