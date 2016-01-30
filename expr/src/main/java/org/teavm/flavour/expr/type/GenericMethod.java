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
package org.teavm.flavour.expr.type;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.teavm.flavour.expr.type.meta.MethodDescriber;

/**
 *
 * @author Alexey Andreev
 */
public class GenericMethod {
    private MethodDescriber describer;
    private GenericClass actualOwner;
    private ValueType[] actualArgumentTypes;
    private ValueType actualReturnType;

    public GenericMethod(MethodDescriber describer, GenericClass actualOwner,
            ValueType[] actualArgumentTypes, ValueType actualReturnType) {
        if (describer.getArgumentTypes().length != actualArgumentTypes.length) {
            throw new IllegalArgumentException();
        }
        if ((actualReturnType == null) != (describer.getReturnType() == null)) {
            throw new IllegalArgumentException();
        }
        this.describer = describer;
        this.actualOwner = actualOwner;
        this.actualArgumentTypes = actualArgumentTypes;
        this.actualReturnType = actualReturnType;
    }

    public GenericClass getActualOwner() {
        return actualOwner;
    }

    public MethodDescriber getDescriber() {
        return describer;
    }

    public ValueType[] getActualArgumentTypes() {
        return actualArgumentTypes.clone();
    }

    public ValueType getActualReturnType() {
        return actualReturnType;
    }

    public GenericMethod substitute(Substitutions substitutions) {
        GenericClass actualOwner = this.actualOwner.substitute(substitutions);
        ValueType[] actualArgumentTypes = this.actualArgumentTypes.clone();
        for (int i = 0; i < actualArgumentTypes.length; ++i) {
            if (actualArgumentTypes[i] instanceof GenericType) {
                actualArgumentTypes[i] = ((GenericType) actualArgumentTypes[i]).substitute(substitutions);
            }
        }
        ValueType actualReturnType = this.actualReturnType;
        if (actualReturnType instanceof GenericType) {
            actualReturnType = ((GenericType) actualReturnType).substitute(substitutions);
        }
        return new GenericMethod(describer, actualOwner, actualArgumentTypes, actualReturnType);
    }

    public GenericMethod newCapture() {
        return substitute(new CapturingSubstitutions(new HashSet<>(Arrays.asList(describer.getTypeVariables()))));
    }

    static class CapturingSubstitutions implements Substitutions {
        Set<TypeVar> capturedTypeVars;

        public CapturingSubstitutions(Set<TypeVar> capturedTypeVars) {
            this.capturedTypeVars = capturedTypeVars;
        }

        @Override
        public GenericType get(TypeVar var) {
            if (var.getName() == null) {
                return null;
            }
            if (capturedTypeVars.contains(var)) {
                TypeVar copy = new TypeVar(var.getName());
                if (var.getUpperBound().isEmpty()) {
                    GenericType[] bounds = var.getUpperBound().stream()
                            .map(bound -> bound.substitute(this))
                            .toArray(sz -> new GenericType[sz]);
                    copy.withUpperBound(bounds);
                } else if (!var.getLowerBound().isEmpty()) {
                    GenericType[] bounds = var.getLowerBound().stream()
                            .map(bound -> bound.substitute(this))
                            .toArray(sz -> new GenericType[sz]);
                    copy.withLowerBound(bounds);
                }
                return new GenericReference(copy);
            } else {
                return null;
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(describer.getName()).append('(');
        ValueTypeFormatter formatter = new ValueTypeFormatter();
        if (actualArgumentTypes.length > 0) {
            formatter.format(actualArgumentTypes[0], sb);
            for (int i = 1; i < actualArgumentTypes.length; ++i) {
                sb.append(", ");
                formatter.format(actualArgumentTypes[i], sb);
            }
        }
        sb.append(')');
        return sb.toString();
    }
}
