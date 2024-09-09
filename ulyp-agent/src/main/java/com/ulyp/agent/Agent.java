package com.ulyp.agent;

import java.lang.instrument.Instrumentation;
import java.util.List;
import java.util.Optional;

import com.ulyp.agent.advice.*;
import com.ulyp.agent.options.AgentOptions;
import com.ulyp.agent.util.ByteBuddyMethodResolver;
import com.ulyp.agent.util.ByteBuddyTypeConverter;
import com.ulyp.agent.util.ErrorLoggingInstrumentationListener;
import com.ulyp.core.recorders.ObjectRecorderRegistry;
import com.ulyp.core.recorders.PrintingRecorder;
import com.ulyp.core.recorders.arrays.ObjectArrayRecorder;
import com.ulyp.core.recorders.collections.CollectionRecorder;
import com.ulyp.core.recorders.collections.CollectionsRecordingMode;
import com.ulyp.core.recorders.collections.MapRecorder;
import com.ulyp.core.util.TypeMatcher;
import com.ulyp.core.util.LoggingSettings;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.scaffold.TypeValidation;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

/**
 * The agent entry point which is invoked by JVM itself
 */
public class Agent {

    private static final String ULYP_LOGO =
        "   __  __    __ __  __    ____ \n" +
            "  / / / /   / / \\ \\/ /   / __ \\\n" +
            " / / / /   / /   \\  /   / /_/ /\n" +
            "/ /_/ /   / /___ / /   / ____/ \n" +
            "\\____/   /_____//_/   /_/      \n" +
            "                               ";

    public static void start(String args, Instrumentation instrumentation) {

        AgentOptions options = new AgentOptions();
        if (!options.isAgentEnabled()) {
            System.out.println("ULYP agent disabled, no code will be instrumented");
            return;
        }

        // Touch first and initialize shadowed slf4j
        String logLevel = LoggingSettings.getLoggingLevel();

        if (AgentContext.isLoaded()) {
            return;
        } else {
            AgentContext.init();
        }
        AgentContext context = AgentContext.getCtx();

        System.out.println(ULYP_LOGO);
        System.out.println("ULYP agent started, logging level = " + logLevel + ", settings: " + options);

        {
            // TODO should have a recorder context with all recorders properly set up
            CollectionRecorder recorder = (CollectionRecorder) ObjectRecorderRegistry.COLLECTION_RECORDER.getInstance();
            recorder.setMode(options.getCollectionsRecordingMode().get());

            MapRecorder mapRecorder = (MapRecorder) ObjectRecorderRegistry.MAP_RECORDER.getInstance();
            mapRecorder.setMode(options.getCollectionsRecordingMode().get());

            PrintingRecorder toStringRecorder = (PrintingRecorder) (ObjectRecorderRegistry.TO_STRING_RECORDER.getInstance());
            toStringRecorder.addTypeMatchers(options.getTypesToPrint().get());

            ObjectArrayRecorder objectArrayRecorder = (ObjectArrayRecorder) ObjectRecorderRegistry.OBJECT_ARRAY_RECORDER.getInstance();
            if (options.getCollectionsRecordingMode().get() != CollectionsRecordingMode.NONE) {
                objectArrayRecorder.setEnabled(true);
            }
        }

        ElementMatcher.Junction<TypeDescription> ignoreMatcher = buildIgnoreMatcher(options);
        ElementMatcher.Junction<TypeDescription> instrumentationMatcher = buildInstrumentationMatcher(options);

        MethodIdFactory methodIdFactory = new MethodIdFactory(context.getMethodRepository());

        AsmVisitorWrapper.ForDeclaredMethods startRecordingMethodAdvice = Advice.withCustomMapping()
                .bind(methodIdFactory)
                .to(StartRecordingMethodAdvice.class)
                .on(buildStartRecordingMethodsMatcher(options));
        AsmVisitorWrapper.ForDeclaredMethods methodCallAdvice = Advice.withCustomMapping()
                .bind(methodIdFactory)
                .to(MethodAdvice.class)
                .on(buildContinueRecordingMethodsMatcher(options).and(x -> x.getParameters().size() > 3));
        AsmVisitorWrapper.ForDeclaredMethods methodCallAdviceNoParams = Advice.withCustomMapping()
                .bind(methodIdFactory)
                .to(MethodAdviceNoArgs.class)
                .on(buildContinueRecordingMethodsMatcher(options).and(x -> x.getParameters().isEmpty()));
        AsmVisitorWrapper.ForDeclaredMethods methodCallAdviceOneParams = Advice.withCustomMapping()
                .bind(methodIdFactory)
                .to(MethodAdviceOneArg.class)
                .on(buildContinueRecordingMethodsMatcher(options).and(x -> x.getParameters().size() == 1));
        AsmVisitorWrapper.ForDeclaredMethods methodCallAdviceTwoParams = Advice.withCustomMapping()
                .bind(methodIdFactory)
                .to(MethodAdviceTwoArgs.class)
                .on(buildContinueRecordingMethodsMatcher(options).and(x -> x.getParameters().size() == 2));
        AsmVisitorWrapper.ForDeclaredMethods methodCallAdviceThreeParams = Advice.withCustomMapping()
                .bind(methodIdFactory)
                .to(MethodAdviceThreeArgs.class)
                .on(buildContinueRecordingMethodsMatcher(options).and(x -> x.getParameters().size() == 3));

        TypeValidation typeValidation = options.isTypeValidationEnabled() ? TypeValidation.ENABLED : TypeValidation.DISABLED;

        AgentBuilder.Identified.Extendable agentBuilder = new AgentBuilder.Default(new ByteBuddy().with(typeValidation))
            .ignore(ignoreMatcher)
            .type(instrumentationMatcher)
            .transform((builder, typeDescription, classLoader, module, protectionDomain) -> builder
                    .visit(methodCallAdviceNoParams)
                    .visit(methodCallAdviceOneParams)
                    .visit(methodCallAdviceTwoParams)
                    .visit(methodCallAdviceThreeParams)
                    .visit(startRecordingMethodAdvice)
                    .visit(methodCallAdvice)
            );

        if (options.isInstrumentConstructorsEnabled()) {
            AsmVisitorWrapper.ForDeclaredMethods startRecordingConstructorAdvice = Advice.withCustomMapping()
                    .bind(methodIdFactory)
                    .to(StartRecordingConstructorAdvice.class)
                    .on(buildStartRecordingConstructorMatcher(options));
            AsmVisitorWrapper.ForDeclaredMethods constructorAdvice = Advice.withCustomMapping()
                    .bind(methodIdFactory)
                    .to(ConstructorAdvice.class)
                    .on(buildContinueRecordingConstructorMatcher(options));

            agentBuilder = agentBuilder.transform((builder, typeDescription, classLoader, module, protectionDomain) ->
                    builder.visit(startRecordingConstructorAdvice).visit(constructorAdvice));
        }

        AgentBuilder agent = agentBuilder.with(AgentBuilder.TypeStrategy.Default.REDEFINE);
        if (options.isInstrumentLambdasEnabled()) {
            agent = agent.with(AgentBuilder.LambdaInstrumentationStrategy.ENABLED);
        }

        if (LoggingSettings.TRACE_ENABLED) {
            agent = agent.with(AgentBuilder.Listener.StreamWriting.toSystemOut());
        } else {
            agent = agent.with(new ErrorLoggingInstrumentationListener());
        }

        agent.installOn(instrumentation);
    }

    private static ElementMatcher.Junction<MethodDescription> buildStartRecordingConstructorMatcher(AgentOptions options) {
        return ElementMatchers.isConstructor().and(
                methodDescription -> options.getRecordMethodList().matches(ByteBuddyMethodResolver.INSTANCE.resolve(methodDescription))
        );
    }

    private static ElementMatcher.Junction<MethodDescription> buildContinueRecordingConstructorMatcher(AgentOptions options) {
        return ElementMatchers.isConstructor().and(
                methodDescription -> !options.getRecordMethodList().matches(ByteBuddyMethodResolver.INSTANCE.resolve(methodDescription))
        );
    }

    private static ElementMatcher.Junction<MethodDescription> buildStartRecordingMethodsMatcher(AgentOptions options) {
        return basicMethodsMatcher(options).and(
                methodDescription -> options.getRecordMethodList().matches(ByteBuddyMethodResolver.INSTANCE.resolve(methodDescription))
        );
    }

    private static ElementMatcher.Junction<MethodDescription> buildContinueRecordingMethodsMatcher(AgentOptions options) {
        return basicMethodsMatcher(options).and(
                methodDescription -> !options.getRecordMethodList().matches(ByteBuddyMethodResolver.INSTANCE.resolve(methodDescription))
        );
    }

    private static ElementMatcher.Junction<MethodDescription> basicMethodsMatcher(AgentOptions options) {
        ElementMatcher.Junction<MethodDescription> methodMatcher = ElementMatchers.isMethod()
                .and(ElementMatchers.not(ElementMatchers.isAbstract()))
                .and(ElementMatchers.not(ElementMatchers.isConstructor()));

        if (options.isInstrumentTypeInitializersEnabled()) {
            return methodMatcher.or(ElementMatchers.isTypeInitializer());
        } else {
            return methodMatcher;
        }
    }

    private static ElementMatcher.Junction<TypeDescription> buildInstrumentationMatcher(AgentOptions options) {
        List<String> instrumentedPackages = options.getInstrumentedPackages().get();
        ElementMatcher.Junction<TypeDescription> instrumentationMatcher = null;

        for (String packageToInstrument : instrumentedPackages) {
            if (instrumentationMatcher == null) {
                instrumentationMatcher = ElementMatchers.nameStartsWith(packageToInstrument);
            } else {
                instrumentationMatcher = instrumentationMatcher.or(ElementMatchers.nameStartsWith(packageToInstrument));
            }
        }

        return Optional.ofNullable(instrumentationMatcher).orElse(ElementMatchers.any());
    }

    private static ElementMatcher.Junction<TypeDescription> buildIgnoreMatcher(AgentOptions options) {
        List<String> excludedPackages = options.getExcludedFromInstrumentationPackages().get();

        ElementMatcher.Junction<TypeDescription> ignoreMatcher = ElementMatchers.nameStartsWith("java.")
            .or(ElementMatchers.nameStartsWith("javax."))
            .or(ElementMatchers.nameStartsWith("jdk."))
            .or(ElementMatchers.nameStartsWith("sun"))
            .or(ElementMatchers.nameStartsWith("shadowed"))
            .or(ElementMatchers.nameStartsWith("com.sun"))
            .or(ElementMatchers.nameStartsWith("com.ulyp"));

        ElementMatcher.Junction<TypeDescription> instrumentationMatcher = buildInstrumentationMatcher(options);
        if (instrumentationMatcher != ElementMatchers.<TypeDescription>any()) {
            ignoreMatcher = ElementMatchers.not(instrumentationMatcher).and(ignoreMatcher);
        }

        for (String excludedPackage : excludedPackages) {
            ignoreMatcher = ignoreMatcher.or(ElementMatchers.nameStartsWith(excludedPackage));
        }

        for (TypeMatcher excludeTypeMatcher : options.getExcludeFromInstrumentationClasses().get()) {
            ByteBuddyTypeConverter typeConverter = ByteBuddyTypeConverter.INSTANCE;
            ignoreMatcher = ignoreMatcher.or(
                target -> excludeTypeMatcher.matches(typeConverter.convert(target.asGenericType()))
            );
        }

        return ignoreMatcher;
    }
}
