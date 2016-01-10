/*
 *  Copyright 2015 Alexey Andreev.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.teavm.flavour.mp;

import java.util.Arrays;
import org.teavm.flavour.mp.reflect.ReflectAnnotatedElement;
import org.teavm.flavour.mp.reflect.ReflectField;
import org.teavm.flavour.mp.reflect.ReflectMethod;

/**
 *
 * @author Alexey Andreev
 */
public interface ReflectClass<T> extends ReflectAnnotatedElement {
    EmitterContext getContext();

    boolean isPrimitive();

    boolean isInterface();

    boolean isArray();

    boolean isAnnotation();

    boolean isEnum();

    T[] getEnumConstants();

    int getModifiers();

    ReflectClass<?> getComponentType();

    String getName();

    ReflectClass<? super T> getSuperclass();

    ReflectClass<? super T>[] getInterfaces();

    boolean isInstance(Object obj);

    T cast(Object obj);

    <U> ReflectClass<U> asSubclass(Class<U> cls);

    default boolean isAssignableFrom(ReflectClass<?> cls) {
        return cls == this || cls.getSuperclass() != null && this.isAssignableFrom(cls.getSuperclass())
                || Arrays.stream(cls.getInterfaces()).anyMatch(c -> isAssignableFrom(c));
    }

    default boolean isAssignableFrom(Class<?> cls) {
        return isAssignableFrom(getContext().findClass(cls));
    }

    ReflectMethod[] getDeclaredMethods();

    ReflectMethod[] getMethods();

    ReflectMethod getDeclaredMethod(String name, ReflectClass<?>... parameterTypes);

    default ReflectMethod getDeclaredJMethod(String name, Class<?>... parameterTypes) {
        ReflectClass<?>[] mappedParamTypes = Arrays.stream(parameterTypes)
                .map(param -> getContext().findClass(param))
                .toArray(sz -> new ReflectClass<?>[sz]);
        return getDeclaredMethod(name, mappedParamTypes);
    }

    ReflectMethod getMethod(String name, ReflectClass<?>... parameterTypes);

    default ReflectMethod getJMethod(String name, Class<?>... parameterTypes) {
        ReflectClass<?>[] mappedParamTypes = Arrays.stream(parameterTypes)
                .map(param -> getContext().findClass(param))
                .toArray(sz -> new ReflectClass<?>[sz]);
        return getMethod(name, mappedParamTypes);
    }

    ReflectField[] getDeclaredFields();

    ReflectField[] getFields();

    ReflectField getDeclaredField(String name);

    ReflectField getField(String name);

    T[] createArray(int size);

    T getArrayElement(Object array, int index);

    int getArrayLength(Object array);

    Class<T> asJavaClass();
}
