package com.ulyp.agent.advice;

import com.ulyp.agent.MethodId;
import com.ulyp.agent.MethodIdFactory;
import com.ulyp.agent.RecorderInstance;
import net.bytebuddy.asm.Advice;

/**
 * Advice which instructs how to instrument constructors. The byte buddy library copies the bytecode of methods into
 * constructors being instrumented.
 */
public class StartRecordingConstructorAdvice {

    /**
     * @param methodId injected right into bytecode unique method id. Mapping is made by
     *                 {@link MethodIdFactory} class.
     */
    @SuppressWarnings("UnusedAssignment")
    @Advice.OnMethodEnter
    static void enter(
            @Advice.Local("callToken") long callToken,
            @MethodId int methodId,
            @Advice.Origin("#t") Class<?> declaringClass,
            @Advice.AllArguments Object[] arguments) {

        // noinspection UnusedAssignment local variable callId is used by exit() method
        callToken = RecorderInstance.instance.startRecordingOnConstructorEnter(methodId, declaringClass, arguments);
    }

    /**
     * @param methodId injected right into bytecode unique method id. Mapping is made by
     *                 {@link MethodIdFactory} class. Guaranteed to be the same
     *                 as for enter advice
     */
    @Advice.OnMethodExit
    static void exit(
            @Advice.Local("callToken") long callToken,
            @MethodId int methodId,
            @Advice.Origin("#t") Class<?> declaringClass,
            @Advice.This Object returnValue) {
        if (callToken > 0) {
            RecorderInstance.instance.onConstructorExit(methodId, returnValue, declaringClass, null, callToken);
        }
    }
}
