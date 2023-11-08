package com.ulyp.storage.tree;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.ExecutionException;

import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.ulyp.core.Method;
import com.ulyp.core.RecordingMetadata;
import com.ulyp.core.Type;
import com.ulyp.core.TypeResolver;
import com.ulyp.core.mem.MethodList;
import com.ulyp.core.mem.RecordedMethodCallList;
import com.ulyp.core.mem.TypeList;
import com.ulyp.core.recorders.StringObjectRecord;
import com.ulyp.core.util.ReflectionBasedTypeResolver;
import com.ulyp.storage.reader.FileRecordingDataReaderBuilder;
import com.ulyp.storage.reader.RecordingDataReader;
import com.ulyp.storage.writer.RecordingDataWriter;
import com.ulyp.storage.writer.FileRecordingDataWriter;

public class CallRecordTreeTest {

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
    private final T obj = new T();
    private RecordingDataReader reader;
    private RecordingDataWriter writer;
    private RecordingMetadata recordingMetadata;

    public static class T {
        public String foo(String in) {
            return in;
        }
    }

    @Before
    public void setUp() throws IOException {
        File file = Files.createTempFile(CallRecordTreeTest.class.getSimpleName(), "a").toFile();
        this.reader = new FileRecordingDataReaderBuilder(file).build();
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
    public void testEmptyTree() throws ExecutionException, InterruptedException {
        CallRecordTree tree = new CallRecordTreeBuilder(reader)
            .setIndexSupplier(InMemoryIndex::new)
            .setReadContinuously(false)
            .build();

        Assert.assertEquals(0, tree.getRecordings().size());
    }

    @Test
    public void testReadWriteRecordingWithoutReturnValue() throws ExecutionException, InterruptedException {
        RecordedMethodCallList calls = new RecordedMethodCallList(recordingId);
        calls.addEnterMethodCall(0, method, typeResolver, obj, new Object[]{"ABC"});
        calls.addExitMethodCall(0, typeResolver, false, "CDE");

        writer.write(recordingMetadata);
        writer.write(types);
        writer.write(methods);
        writer.write(calls);
        writer.close();

        CallRecordTree tree = new CallRecordTreeBuilder(reader)
            .setIndexSupplier(InMemoryIndex::new)
            .setReadContinuously(false)
            .build();

        Assert.assertEquals(1, tree.getRecordings().size());

        Recording recording = tree.getRecordings().iterator().next();
        CallRecord root = recording.getRoot();
        Assert.assertTrue(root.isFullyRecorded());

        StringObjectRecord returnValue = (StringObjectRecord) root.getReturnValue();
        Assert.assertThat(returnValue.value(), Matchers.is("CDE"));
    }

    @Test
    public void testNotFinishedRecording() throws ExecutionException, InterruptedException {
        RecordedMethodCallList calls = new RecordedMethodCallList(recordingId);
        calls.addEnterMethodCall(0, method, typeResolver, obj, new Object[]{"ABC"});

        writer.write(recordingMetadata);
        writer.write(types);
        writer.write(methods);
        writer.write(calls);
        writer.close();

        CallRecordTree tree = new CallRecordTree(reader, RecordingListener.empty(), InMemoryIndex::new, true);
        tree.getCompleteFuture().get();

        Assert.assertEquals(1, tree.getRecordings().size());

        Recording recording = tree.getRecordings().iterator().next();
        CallRecord root = recording.getRoot();
        Assert.assertFalse(root.isFullyRecorded());
    }
}
