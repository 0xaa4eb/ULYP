package com.ulyp.core;

import com.ulyp.core.mem.RecordedMethodCallList;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * A collection of enter and exit recorded method calls for a certain recording session.
 */
@NotThreadSafe
@Slf4j
public class CallRecordBuffer {

    @Getter
    private final RecordedMethodCallList recordedCalls;
    private final int recordingId;
    private final int rootCallId;

    private int lastExitCallId = -1;
    private int nextCallId;

    public CallRecordBuffer(int recordingId) {
        this.recordingId = recordingId;
        this.nextCallId = 1;
        this.rootCallId = 1;
        this.recordedCalls = new RecordedMethodCallList(recordingId);
    }

    private CallRecordBuffer(int recordingId, int nextCallId, int rootCallId) {
        this.recordingId = recordingId;
        this.nextCallId = nextCallId;
        this.rootCallId = rootCallId;
        this.recordedCalls = new RecordedMethodCallList(recordingId);
    }

    public CallRecordBuffer cloneWithoutData() {
        return new CallRecordBuffer(this.recordingId, this.nextCallId, rootCallId);
    }

    public long estimateBytesSize() {
        return recordedCalls.getRawBytes().byteLength();
    }

    public int recordMethodEnter(TypeResolver typeResolver, Method method, @Nullable Object callee, Object[] args) {
        try {

            int callId = nextCallId++;
            recordedCalls.addEnterMethodCall(
                    callId,
                    method,
                    typeResolver,
                    callee,
                    args
            );
            return callId;
        } catch (Throwable err) {
            // catch Throwable intentionally. While recording is done anything can happen, but the app which uses ulyp should not be disrupted
            log.error("Error while recording", err);
            return -1;
        }
    }

    public void recordMethodExit(TypeResolver typeResolver, Object returnValue, Throwable thrown, int callId) {
        if (callId >= 0) {
            if (thrown == null) {
                recordedCalls.addExitMethodCall(
                        callId,
                    typeResolver,
                    false,
                    returnValue
                );
            } else {
                recordedCalls.addExitMethodCall(
                        callId,
                    typeResolver,
                    true,
                    thrown
                );
            }
            lastExitCallId = callId;
        }
    }

    public boolean isComplete() {
        return lastExitCallId == rootCallId;
    }

    public long getTotalRecordedEnterCalls() {
        return nextCallId - rootCallId;
    }

    public int getRecordedCallsSize() {
        return recordedCalls.size();
    }

    @Override
    public String toString() {
        return "CallRecordBuffer{}";
    }
}
