package com.ulyp.agent;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;

import com.ulyp.agent.policy.EnabledByDefaultRecordingPolicy;
import com.ulyp.core.Method;
import com.ulyp.core.RecordedMethodCall;
import com.ulyp.core.TypeResolver;
import com.ulyp.core.util.ReflectionBasedMethodResolver;
import com.ulyp.core.util.ReflectionBasedTypeResolver;
import com.ulyp.storage.util.HeapStorageWrtiter;

import static org.junit.Assert.*;

public class RecorderTest {

    private static class X {
        public String foo(Integer s) {
            return s.toString();
        }
    }

    private final HeapStorageWrtiter storage = new HeapStorageWrtiter();
    private final Recorder recorder = new Recorder(new CallIdGenerator(), new EnabledByDefaultRecordingPolicy(), storage);
    private final TypeResolver typeResolver = new ReflectionBasedTypeResolver();
    private final ReflectionBasedMethodResolver methodResolver = new ReflectionBasedMethodResolver();
    private Method method;

    @Before
    public void setUp() throws NoSuchMethodException {
        method = methodResolver.resolve(X.class.getMethod("foo", Integer.class));
    }

    @Test
    public void shouldRecordDataWhenRecordingIsFinished() {
        X recorded = new X();
        long callId = recorder.startOrContinueRecordingOnMethodEnter(typeResolver, method, recorded, new Object[5]);
        recorder.onMethodExit(typeResolver, method, "ABC", null, callId);

        assertNull(recorder.getRecordingState());
        assertEquals(2, storage.getCallRecords().size());
    }

    @Test
    public void testTemporaryRecordingDisableWithOngoingRecording() {

        X recorded = new X();
        long callId1 = recorder.startOrContinueRecordingOnMethodEnter(typeResolver, method, recorded, new Object[5]);

        recorder.disableRecording();

        long callId2 = recorder.onMethodEnter(method, recorded, new Object[]{10});
        recorder.onMethodExit(typeResolver, method, "CDE", null, callId2);

        recorder.enableRecording();

        recorder.onMethodExit(typeResolver, method, "ABC", null, callId1);

        assertEquals(2, storage.getCallRecords().size());

        // only the callId1 calls are recorded
        assertEquals(new HashSet<>(Collections.singletonList(callId1)), storage.getCallRecords()
            .stream()
            .map(RecordedMethodCall::getCallId)
            .collect(Collectors.toSet())
        );
    }

    @Test
    public void testTemporaryRecordingDisableWithNoOngoingRecording() {
        recorder.disableRecording();

        recorder.enableRecording();

        assertNull(recorder.getRecordingState());
    }
}