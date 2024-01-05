package com.ulyp.core.recorders;

import com.ulyp.core.Type;
import com.ulyp.core.recorders.arrays.ByteArrayRecorder;
import com.ulyp.core.recorders.arrays.ObjectArrayRecorder;
import com.ulyp.core.recorders.collections.CollectionRecorder;
import com.ulyp.core.recorders.collections.MapRecorder;

/**
 * All object recorer registry. Every recorder is provided with two values: a unique id and an order.
 * <p>
 * An order is used to sort all recorders. When ulyp wants to find out which recorder should be used
 * for a particular {@link Type} it checks all available recorders sorted with the order
 * using method {@link ObjectRecorder#supports(Class)}.
 * Recorders which support certain frequently used types come first, before rare types recorders to optimize
 * check time. The more specific (i.e. narrow) recorder is, the earlier it should be checked for any type.
 * <p>
 * The last one to come is {@link IdentityRecorder} which can record any type.
 * <p>
 * The resolution of recorders is done only once for each method and cached within {@link com.ulyp.core.Method}
 */
public enum ObjectRecorderRegistry {
    CLASS_OBJECT_RECORDER(new ClassObjectRecorder((byte) 1), 20),
    STRING_RECORDER(new StringRecorder((byte) 2), 0),
    THROWABLE_RECORDER(new ThrowableRecorder((byte) 5), 20),
    ENUM_RECORDER(new EnumRecorder((byte) 6), 5),
    INTEGRAL_RECORDER(new IntegralRecorder((byte) 12), 0),
    BOOLEAN_RECORDER(new BooleanRecorder((byte) 100), 1),
    QUEUE_IDENTITY_RECORDER(new CallRecordQueueIdentityObjectRecorder((byte) 101), 95),
    ANY_NUMBER_RECORDER(new NumbersRecorder((byte) 8), 10),
    OBJECT_ARRAY_RECORDER(new ObjectArrayRecorder((byte) 11), 1),
    BYTE_ARRAY_RECORDER(new ByteArrayRecorder((byte) 29), 1),
    COLLECTION_RECORDER(new CollectionRecorder((byte) 10), 1),
    MAP_RECORDER(new MapRecorder((byte) 13), 1),
    OPTIONAL_RECORDER(new OptionalRecorder((byte) 25), 90),
    CHAR_RECORDER(new CharRecorder((byte) 28), 8),
    FILE_RECORDER(new FileRecorder((byte) 26), 90),
    PATH_RECORDER(new PathRecorder((byte) 27), 90),
    DATE_RECORDER(new DateRecorder((byte) 20), 90),
    TO_STRING_RECORDER(new PrintingRecorder((byte) 91), 99),
    IDENTITY_RECORDER(new IdentityRecorder((byte) 0), Integer.MAX_VALUE / 2),
    // Null recorder is only used manually, so it has max available order
    NULL_RECORDER(new NullObjectRecorder((byte) 9), Integer.MAX_VALUE);

    public static final ObjectRecorder[] recorderInstances = new ObjectRecorder[256];

    static {
        for (ObjectRecorderRegistry type : values()) {
            if (recorderInstances[type.getInstance().getId()] != null) {
                throw new RuntimeException("Duplicate id");
            }
            recorderInstances[type.getInstance().getId()] = type.getInstance();
        }
    }

    private final ObjectRecorder instance;
    private final int order;

    ObjectRecorderRegistry(ObjectRecorder instance, int order) {
        this.instance = instance;
        this.order = order;
    }

    public static ObjectRecorder recorderForId(byte id) {
        return recorderInstances[id];
    }

    public ObjectRecorder getInstance() {
        return instance;
    }

    public int getOrder() {
        return order;
    }
}
