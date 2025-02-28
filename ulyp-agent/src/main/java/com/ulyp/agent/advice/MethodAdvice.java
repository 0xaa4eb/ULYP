package com.ulyp.agent.advice;

import com.ulyp.agent.*;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

/**
 * Advice which instructs how to instrument methods. The byte buddy library copies the bytecode of methods into
 * constructors being instrumented.
 */
public class MethodAdvice {

    /**
     * @param methodId injected right into bytecode unique method id. Mapping is made by
     *                 {@link MethodIdFactory} class.
     */
    @Advice.OnMethodEnter
    static void enter(
            @MethodId int methodId,
            @Advice.Local("callToken") long callToken,
            @Advice.This(optional = true) Object callee,
            @Advice.AllArguments Object[] arguments) {

        if (Recorder.currentRecordingSessionCount.get() > 0) {
            RecordingThreadLocalContext recordingCtx = RecorderInstance.instance.getCtx();
            if (recordingCtx != null) {
                //noinspection UnusedAssignment
                callToken = RecorderInstance.instance.onMethodEnter(recordingCtx, methodId, callee, arguments);
            }
        }
    }

    /**
     * @param methodId injected right into bytecode unique method id. Mapping is made by
     *                 {@link MethodIdFactory} class. Guaranteed to be the same
     *                 as for enter advice
     */
    @Advice.OnMethodExit(onThrowable = Throwable.class)
    static void exit(
            @MethodId int methodId,
            @Advice.Local("callToken") long callToken,
            @Advice.Thrown Throwable throwable,
            @Advice.Return(typing = Assigner.Typing.DYNAMIC) Object returnValue) {
        if (callToken > 0) {
            RecorderInstance.instance.onMethodExit(methodId, returnValue, throwable, callToken);
        }
    }
}
