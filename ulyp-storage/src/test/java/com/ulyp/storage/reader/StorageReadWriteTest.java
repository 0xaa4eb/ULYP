/*
package com.ulyp.storage.reader;

import com.ulyp.core.*;
import com.ulyp.core.mem.MethodList;
import com.ulyp.core.mem.RecordedMethodCallList;
import com.ulyp.core.mem.TypeList;
import com.ulyp.core.recorders.*;
import com.ulyp.core.util.ReflectionBasedTypeResolver;
import com.ulyp.storage.tree.CallRecord;
import com.ulyp.storage.ReaderSettings;
import com.ulyp.storage.tree.Recording;
import com.ulyp.storage.reader.RecordingDataReader;
import com.ulyp.storage.writer.RecordingDataWriter;
import com.ulyp.storage.writer.FileRecordingDataWriter;

import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;

import static org.junit.Assert.*;

@Ignore
public class StorageReadWriteTest {

    private final int recordingId = 42;
    private final TypeResolver typeResolver = new ReflectionBasedTypeResolver();
    private final Type type = typeResolver.get(T.class);
    private final Method method = Method.builder()
            .declaringType(type)
            .name("run")
            .id(1000)
            .isConstructor(false)
            .isStatic(false)
            .returnsSomething(true)
            .build();
    private final TypeList types = new TypeList();
    private final MethodList methods = new MethodList();
    private RecordingDataReader reader;
    private RecordingDataWriter writer;
    private RecordingMetadata recordingMetadata;

    @Before
    public void setUp() throws IOException {
        File file = Files.createTempFile(StorageReadWriteTest.class.getSimpleName(), "a").toFile();
        this.reader = new AsyncFileRecordingDataReader(ReaderSettings.builder().file(file).autoStartReading(true).build());
        this.writer = new FileRecordingDataWriter(file);

        recordingMetadata = RecordingMetadata.builder()
                .id(recordingId)
                .recordingStartedEpochMillis(System.currentTimeMillis())
                .threadName("Thread-1")
                .threadId(4343L)
                .build();

        types.add(type);
        methods.add(method);
    }

    @After
    public void tearDown() {
        reader.close();
        writer.close();
    }

    @Test
    public void testReadWriteRecordingWithoutReturnValue() {
        T callee = new T();

        RecordedMethodCallList methodCalls = new RecordedMethodCallList(recordingId);

        methodCalls.addEnterMethodCall(
                0,
                method,
                typeResolver,
                callee,
                new Object[]{"ABC"}
        );


        writer.write(recordingMetadata);
        writer.write(types);
        writer.write(methods);
        writer.write(methodCalls);


        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(
                        () -> {
                            assertEquals(1, reader.getRecordings().size());

                            Recording recording = reader.getRecordings().get(0);
                            CallRecord root = recording.getRoot();
                            assertNotNull(root);

                            assertEquals(1, root.getArgs().size());
                            assertEquals(1, root.getSubtreeSize());
                            StringObjectRecord argRecorded = (StringObjectRecord) root.getArgs().get(0);
                            assertEquals("ABC", argRecorded.value());

                            IdentityObjectRecord calleeRecorded = (IdentityObjectRecord) root.getCallee();
                            assertEquals(System.identityHashCode(callee), calleeRecorded.getHashCode());

                            assertFalse(root.hasThrown());
                            assertEquals(NotRecordedObjectRecord.getInstance(), root.getReturnValue());
                            assertEquals(0, root.getChildren().size());
                        }
                );
    }

    @Test
    public void testReadWriteRecordingWithReturnValue() {
        T callee = new T();

        RecordedMethodCallList methodCalls = new RecordedMethodCallList(recordingId);

        methodCalls.addEnterMethodCall(
                0,
                method,
                typeResolver,
                callee,
                new Object[]{"ABC"}
        );

        methodCalls.addExitMethodCall(
                0,
                typeResolver,
                false,
                "DEF"
        );


        writer.write(recordingMetadata);
        writer.write(types);
        writer.write(methods);
        writer.write(methodCalls);


        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(
                        () -> {
                            assertEquals(1, reader.getRecordings().size());

                            Recording recording = reader.getRecordings().get(0);
                            CallRecord root = recording.getRoot();
                            assertNotNull(root);

                            assertEquals(1, root.getArgs().size());
                            assertEquals(1, root.getSubtreeSize());

                            StringObjectRecord argRecorded = (StringObjectRecord) root.getArgs().get(0);
                            assertEquals("ABC", argRecorded.value());

                            IdentityObjectRecord calleeRecorded = (IdentityObjectRecord) root.getCallee();
                            assertEquals(System.identityHashCode(callee), calleeRecorded.getHashCode());

                            StringObjectRecord returnValueRecorded = (StringObjectRecord) root.getReturnValue();
                            assertEquals("DEF", returnValueRecorded.value());
                        }
                );
    }

    @Test
    public void testReadWriteWithCoupleOfChildren() {
        T callee = new T();

        RecordedMethodCallList methodCalls = new RecordedMethodCallList(recordingId);

        methodCalls.addEnterMethodCall(
                0,
                method,
                typeResolver,
                callee,
                new Object[]{"ABC"}
        );

        methodCalls.addEnterMethodCall(
                1,
                method,
                typeResolver,
                callee,
                new Object[]{"XYZ"}
        );

        methodCalls.addExitMethodCall(
                1,
                typeResolver,
                false,
                "GHJ"
        );

        methodCalls.addEnterMethodCall(
                2,
                method,
                typeResolver,
                callee,
                new Object[]{"BHJ"}
        );


        writer.write(recordingMetadata);
        writer.write(types);
        writer.write(methods);
        writer.write(methodCalls);


        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(
                        () -> {
                            assertEquals(1, reader.getRecordings().size());

                            Recording recording = reader.getRecordings().get(0);
                            CallRecord root = recording.getRoot();
                            assertNotNull(root);

                            assertEquals(3, root.getSubtreeSize());
                            assertEquals(2, root.getChildren().size());

                            CallRecord child1 = root.getChildren().get(0);

                            assertTrue(child1.isFullyRecorded());

                            CallRecord child2 = root.getChildren().get(1);

                            assertFalse(child2.isFullyRecorded());
                        }
                );


        methodCalls = new RecordedMethodCallList(recordingId);

        methodCalls.addExitMethodCall(
                2,
                typeResolver,
                false,
                "HJK"
        );

        methodCalls.addExitMethodCall(
                0,
                typeResolver,
                false,
                "UIO"
        );

        writer.write(methodCalls);


        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(
                        () -> {
                            assertEquals(1, reader.getRecordings().size());

                            Recording recording = reader.getRecordings().get(0);
                            CallRecord root = recording.getRoot();
                            assertNotNull(root);

                            assertTrue(root.isFullyRecorded());
                            assertEquals(3, root.getSubtreeSize());
                            assertEquals(2, root.getChildren().size());

                            CallRecord child1 = root.getChildren().get(0);

                            assertTrue(child1.isFullyRecorded());

                            CallRecord child2 = root.getChildren().get(1);

                            assertTrue(child2.isFullyRecorded());
                        }
                );
    }

    public static class T {
        public String foo(String in) {
            return in;
        }
    }
}*/
