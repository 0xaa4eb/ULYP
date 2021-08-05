package com.ulyp.agent;

import com.ulyp.agent.transport.CallRecordTreeRequest;
import com.ulyp.agent.util.EnhancedThreadLocal;
import com.ulyp.agent.util.StartRecordingPolicy;
import com.ulyp.core.*;
import com.ulyp.core.log.AgentLogManager;
import com.ulyp.core.log.Logger;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings("unused")
@ThreadSafe
public class Recorder {

    private static final Logger LOGGER = AgentLogManager.getLogger(Recorder.class);
    private static final Recorder instance = new Recorder(AgentContext.getInstance());

    /**
     * Keeps current recording session count. Based on the fact that most of the time there is no
     * recording sessions and this counter is equal to 0, it's possible to make a small performance optimization.
     * Advice code (see RecordingAdvice class) can first check if there are any recording sessions are active at all. If there are any,
     * then advice code will check thread local and know if there is recording session in this thread precisely.
     * This helps minimizing thread local lookups in the advice code
     */
    public static final AtomicInteger currentRecordingSessionCount = new AtomicInteger();

    public static Recorder getInstance() {
        return instance;
    }

    private final EnhancedThreadLocal<CallRecordLog> threadLocalRecordsLog = new EnhancedThreadLocal<>();
    private final ThreadLocal<Long> callIdThreadLocal = ThreadLocal.withInitial(() -> 1L);
    private final StartRecordingPolicy startRecordingPolicy;
    private final AgentContext context;

    public Recorder(AgentContext context) {
        this.context = context;
        this.startRecordingPolicy = context.getSysPropsSettings().getStartRecordingPolicy();
    }

    public boolean recordingIsActiveInCurrentThread() {
        return threadLocalRecordsLog.get() != null;
    }

    public long startOrContinueRecordingOnMethodEnter(TypeResolver typeResolver, Method method, @Nullable Object callee, Object[] args) {
        if (startRecordingPolicy.canStartRecording()) {
            threadLocalRecordsLog.computeIfAbsent(() -> {
                CallRecordLog log = new CallRecordLog(typeResolver, callIdThreadLocal.get() + 1);
                currentRecordingSessionCount.incrementAndGet();
                LOGGER.info("Started recording " + log.getRecordingId() + " at method " + method.toPrettyString());
                return log;
            });
        }

        return onMethodEnter(method, callee, args);
    }

    public long startOrContinueRecordingOnConstructorEnter(TypeResolver typeResolver, Method method, Object[] args) {
        if (startRecordingPolicy.canStartRecording()) {
            threadLocalRecordsLog.computeIfAbsent(() -> {
                CallRecordLog log = new CallRecordLog(typeResolver, callIdThreadLocal.get());
                currentRecordingSessionCount.incrementAndGet();
                LOGGER.info("Started recording " + log.getRecordingId() + " at method " + method.toPrettyString());
                return log;
            });
        }

        return onConstructorEnter(method, args);
    }

    public void endRecordingIfPossibleOnMethodExit(TypeResolver typeResolver, Method method, Object result, Throwable thrown, long callId) {
        onMethodExit(typeResolver, method, result, thrown, callId);

        CallRecordLog recordLog = threadLocalRecordsLog.get();
        if (recordLog != null && recordLog.isComplete()) {
            threadLocalRecordsLog.clear();
            currentRecordingSessionCount.decrementAndGet();
            LOGGER.info("Finished recording " + recordLog.getRecordingId() + ", recorded " + recordLog.getCallsRecorded() + " calls");
            context.getTransport().uploadAsync(
                    new CallRecordTreeRequest(
                            recordLog,
                            MethodDescriptionMap.getInstance().values(),
                            typeResolver.getAllKnownTypes(),
                            context.getProcessInfo()
                    )
            );
        }
    }

    public void endRecordingIfPossibleOnConstructorExit(TypeResolver typeResolver, Method method, long callId, Object result) {
        onConstructorExit(typeResolver, method, result, callId);

        CallRecordLog recordLog = threadLocalRecordsLog.get();
        if (recordLog != null && recordLog.isComplete()) {
            threadLocalRecordsLog.clear();
            currentRecordingSessionCount.decrementAndGet();
            callIdThreadLocal.set(recordLog.getLastCallId() + 1);
            LOGGER.info("Finished recording " + recordLog.getRecordingId() + ", recorded " + recordLog.getCallsRecorded() + " calls");
            context.getTransport().uploadAsync(
                    new CallRecordTreeRequest(
                            recordLog,
                            MethodDescriptionMap.getInstance().values(),
                            typeResolver.getAllKnownTypes(),
                            context.getProcessInfo()
                    )
            );
        }
    }

    public long onConstructorEnter(Method method, Object[] args) {
        return onMethodEnter(method, null, args);
    }

    public long onMethodEnter(Method method, @Nullable Object callee, Object[] args) {
        CallRecordLog callRecordLog = threadLocalRecordsLog.get();
        if (callRecordLog == null) {
            return -1;
        }
        return callRecordLog.onMethodEnter(method, callee, args);
    }

    public void onConstructorExit(TypeResolver typeResolver, Method method, Object result, long callId) {
        onMethodExit(typeResolver, method, result,null, callId);
    }

    public void onMethodExit(TypeResolver typeResolver, Method method, Object result, Throwable thrown, long callId) {
        CallRecordLog currentRecordLog = threadLocalRecordsLog.get();
        if (currentRecordLog == null) return;
        currentRecordLog.onMethodExit(method.getId(), method.getReturnValuePrinter(), result, thrown, callId);

        if (currentRecordLog.estimateBytesSize() > 32 * 1024 * 1024 || (System.currentTimeMillis() - currentRecordLog.getEpochMillisCreatedTime()) > 100) {
            CallRecordLog newRecordLog = currentRecordLog.cloneWithoutData();
            threadLocalRecordsLog.set(newRecordLog);

            context.getTransport().uploadAsync(
                    new CallRecordTreeRequest(
                            currentRecordLog,
                            MethodDescriptionMap.getInstance().values(),
                            typeResolver.getAllKnownTypes(),
                            context.getProcessInfo()
                    )
            );
        }
    }
}
