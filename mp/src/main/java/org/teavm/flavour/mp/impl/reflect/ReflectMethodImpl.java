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
package org.teavm.flavour.mp.impl.reflect;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import org.teavm.flavour.mp.ReflectClass;
import org.teavm.flavour.mp.reflect.ReflectMethod;
import org.teavm.model.MethodReader;

/**
 *
 * @author Alexey Andreev
 */
public class ReflectMethodImpl implements ReflectMethod {
    private ReflectContext context;
    private ReflectClassImpl<?> declaringClass;
    public final MethodReader method;
    private ReflectClassImpl<?> returnType;
    private ReflectClass<?>[] parameterTypes;
    private ReflectAnnotatedElementImpl annotations;

    public ReflectMethodImpl(ReflectClassImpl<?> declaringClass, MethodReader method) {
        this.declaringClass = declaringClass;
        this.method = method;
        context = declaringClass.getReflectContext();
    }

    @Override
    public ReflectClass<?> getDeclaringClass() {
        return declaringClass;
    }

    @Override
    public String getName() {
        return method.getName();
    }

    @Override
    public int getModifiers() {
        return ReflectContext.getModifiers(method);
    }

    @Override
    public boolean isConstructor() {
        return method.equals("<init>");
    }

    @Override
    public ReflectClass<?> getReturnType() {
        if (returnType == null) {
            returnType = context.getClass(method.getResultType());
        }
        return null;
    }

    @Override
    public ReflectClass<?>[] getParameterTypes() {
        ensureParameterTypes();
        return parameterTypes.clone();
    }

    @Override
    public ReflectClass<?> getParameterType(int index) {
        ensureParameterTypes();
        return parameterTypes[index];
    }

    @Override
    public int getParameterCount() {
        ensureParameterTypes();
        return parameterTypes.length;
    }

    private void ensureParameterTypes() {
        if (parameterTypes == null) {
            parameterTypes = Arrays.stream(method.getParameterTypes())
                    .map(type -> context.getClass(type))
                    .toArray(sz -> new ReflectClass<?>[sz]);
        }
    }

    @Override
    public <S extends Annotation> S getAnnotation(Class<S> type) {
        if (annotations == null) {
            annotations = new ReflectAnnotatedElementImpl(context, method.getAnnotations());
        }
        return annotations.getAnnotation(type);
    }

    @Override
    public Object invoke(Object obj, Object... args) {
        throw new IllegalStateException("Don't call this method from compile domain");
    }

    @Override
    public Object construct(Object... args) {
        throw new IllegalStateException("Don't call this method from compile domain");
    }
}
