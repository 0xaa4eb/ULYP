package com.ulyp.core;

import com.ulyp.core.util.ConcurrentArrayList;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Is used at runtime to resolve object's type in order to determine what recorder should be used.
 */
@ThreadSafe
public interface TypeResolver {

    @NotNull
    Type get(Object o);

    @NotNull
    Type get(Class<?> clazz);

    @NotNull
    Collection<Type> getAllResolved();

    @NotNull
    ConcurrentArrayList<Type> getAllResolvedAsConcurrentList();
}
