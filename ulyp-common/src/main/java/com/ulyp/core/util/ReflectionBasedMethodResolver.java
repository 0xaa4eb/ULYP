package com.ulyp.core.util;

import com.ulyp.core.Method;
import com.ulyp.core.Type;
import com.ulyp.core.TypeResolver;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.TestOnly;

import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@TestOnly
public class ReflectionBasedMethodResolver {

    private static final AtomicInteger idGenerator = new AtomicInteger();

    private final TypeResolver typeResolver = ReflectionBasedTypeResolver.getInstance();

    public Method resolve(java.lang.reflect.Method method) {
        boolean returns = method.getReturnType() != Void.class;
        Type declaringType = typeResolver.get(method.getDeclaringClass());

        return Method.builder()
            .id(idGenerator.incrementAndGet())
            .name(method.getName())
            .constructor(false)
            .isStatic(Modifier.isStatic(method.getModifiers()))
            .returnsSomething(returns)
            .type(declaringType)
            .build();
    }
}
