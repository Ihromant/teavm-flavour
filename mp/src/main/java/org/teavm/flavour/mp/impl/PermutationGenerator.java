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
package org.teavm.flavour.mp.impl;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.teavm.dependency.DependencyAgent;
import org.teavm.dependency.DependencyType;
import org.teavm.dependency.MethodDependency;
import org.teavm.diagnostics.Diagnostics;
import org.teavm.flavour.mp.Emitter;
import org.teavm.flavour.mp.ReflectClass;
import org.teavm.flavour.mp.impl.meta.ParameterKind;
import org.teavm.flavour.mp.impl.meta.ProxyModel;
import org.teavm.flavour.mp.impl.meta.ProxyParameter;
import org.teavm.model.BasicBlock;
import org.teavm.model.CallLocation;
import org.teavm.model.MethodReference;
import org.teavm.model.Program;
import org.teavm.model.ValueType;
import org.teavm.model.Variable;
import org.teavm.model.instructions.InvocationType;
import org.teavm.model.instructions.InvokeInstruction;

/**
 *
 * @author Alexey Andreev
 */
class PermutationGenerator {
    private int suffixGenerator;
    DependencyAgent agent;
    private EmitterContextImpl emitterContext;
    ProxyModel model;
    MethodDependency methodDep;
    CallLocation location;
    Diagnostics diagnostics;
    EmitterImpl<Object> emitter;
    Method proxyMethod;
    String[][] variants;
    int[] indexes;

    public PermutationGenerator(DependencyAgent agent, ProxyModel model, MethodDependency methodDep,
            CallLocation location, EmitterContextImpl emitterContext) {
        this.agent = agent;
        this.diagnostics = agent.getDiagnostics();
        this.model = model;
        this.methodDep = methodDep;
        this.location = location;
        this.emitterContext = emitterContext;
    }

    public void installProxyEmitter() {
        Diagnostics diagnostics = agent.getDiagnostics();

        MethodDependency getClassDep = agent.linkMethod(new MethodReference(Object.class, "getClass", Class.class),
                location);
        getClassDep.getThrown().connect(methodDep.getThrown());

        ProxyClassLoader proxyClassLoader = new ProxyClassLoader(agent.getClassLoader(),
                model.getProxyMethod().getClassName());
        try {
            proxyMethod = getJavaMethod(proxyClassLoader, model.getProxyMethod());
            proxyMethod.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            StringWriter stackTraceWriter = new StringWriter();
            e.printStackTrace(new PrintWriter(stackTraceWriter));
            diagnostics.error(location, "Error accessing proxy method {{m0}}: " + stackTraceWriter.getBuffer(),
                    model.getProxyMethod());
            return;
        }

        boolean dependenciesInstalled = false;
        for (ProxyParameter param : model.getParameters()) {
            if (param.getKind() == ParameterKind.REFLECT_VALUE) {
                methodDep.getVariable(param.getIndex() + 1).addConsumer(type -> consumeType(param, type, getClassDep));
                dependenciesInstalled = true;
            }
        }

        if (!dependenciesInstalled) {
            emitPermutation(null, null, getClassDep);
        }

        installAdditionalDependencies(getClassDep);
    }

    private void installAdditionalDependencies(MethodDependency getClassDep) {
        MethodDependency sbInitDep = agent.linkMethod(new MethodReference(StringBuilder.class, "<init>", void.class),
                location);
        sbInitDep.getThrown().connect(methodDep.getThrown());
        sbInitDep.getVariable(0).propagate(agent.getType(StringBuilder.class.getName()));
        sbInitDep.use();

        MethodDependency sbAppendDep = agent.linkMethod(new MethodReference(StringBuilder.class, "append",
                String.class, StringBuilder.class), location);
        sbAppendDep.getThrown().connect(methodDep.getThrown());
        sbAppendDep.getVariable(0).propagate(agent.getType(StringBuilder.class.getName()));
        sbAppendDep.getVariable(1).propagate(agent.getType(String.class.getName()));
        sbAppendDep.use();

        MethodDependency sbToStringDep = agent.linkMethod(new MethodReference(StringBuilder.class, "toString",
                String.class), location);
        sbToStringDep.getThrown().connect(methodDep.getThrown());
        sbToStringDep.getVariable(0).propagate(agent.getType(StringBuilder.class.getName()));
        sbToStringDep.use();

        MethodDependency nameDep = agent.linkMethod(new MethodReference(Class.class, "getName", String.class),
                location);
        getClassDep.getResult().connect(nameDep.getVariable(0));
        nameDep.getThrown().connect(methodDep.getThrown());
        nameDep.use();

        MethodDependency equalsDep = agent.linkMethod(new MethodReference(String.class, "equals", Object.class,
                boolean.class), location);
        nameDep.getResult().connect(equalsDep.getVariable(0));
        equalsDep.getVariable(1).propagate(agent.getType("java.lang.String"));
        equalsDep.getThrown().connect(methodDep.getThrown());
        equalsDep.use();

        MethodDependency hashCodeDep = agent.linkMethod(new MethodReference(String.class, "hashCode", int.class),
                location);
        nameDep.getResult().connect(hashCodeDep.getVariable(0));
        hashCodeDep.getThrown().connect(methodDep.getThrown());
        hashCodeDep.use();
    }

    private void consumeType(ProxyParameter param, DependencyType type, MethodDependency getClassDep) {
        variants = new String[model.getParameters().size()][];
        indexes = new int[variants.length];
        for (ProxyParameter otherParam : model.getParameters()) {
            if (otherParam == param || otherParam.getKind() != ParameterKind.REFLECT_VALUE) {
                continue;
            }
            String[] types = methodDep.getVariable(otherParam.getIndex()).getTypes();
            if (types.length == 0) {
                return;
            }
            variants[otherParam.getIndex()] = types;
        }

        int i;
        do {
            emitPermutation(param, type, getClassDep);
            for (i = 0; i < variants.length; ++i) {
                if (variants[i] != null) {
                    if (++indexes[i] < variants[i].length) {
                        break;
                    }
                    indexes[i] = 0;
                }
            }
        } while (i < variants.length);
    }

    private void emitPermutation(ProxyParameter masterParam, DependencyType type, MethodDependency getClassDep) {
        emitter = new EmitterImpl<>(emitterContext, agent.getClassSource(), new CompositeMethodGenerator(diagnostics),
                model.getProxyMethod(), model.getMethod().getReturnType());

        for (int i = 0; i <= model.getParameters().size(); ++i) {
            emitter.generator.getProgram().createVariable();
        }

        MethodReference implRef = buildMethodReference(masterParam, type);

        Object[] proxyArgs = new Object[model.getCallParameters().size() + 1];
        proxyArgs[0] = getContextParameter(model.getProxyMethod().parameterType(0));

        for (int i = 0; i < model.getCallParameters().size(); ++i) {
            ProxyParameter param = model.getCallParameters().get(i);
            int j = param.getIndex();
            switch (param.getKind()) {
                case CONSTANT:
                    proxyArgs[i + 1] = param.getValue();
                    break;
                case VALUE:
                    proxyArgs[i + 1] = new ValueImpl<>(getParameterVar(param));
                    break;
                case REFLECT_VALUE: {
                    ReflectClass<?> cls = emitterContext.findClass(param != masterParam
                            ? variants[j][indexes[j]] : type.getName());
                    proxyArgs[i + 1] = new ReflectValueImpl<>(getParameterVar(param), cls);
                    break;
                }
            }
        }

        try {
            proxyMethod.invoke(null, proxyArgs);
            emitter.close();
            agent.submitMethod(implRef, emitter.generator.getProgram());
        } catch (IllegalAccessException | InvocationTargetException e) {
            StringWriter writer = new StringWriter();
            e.printStackTrace(new PrintWriter(writer));
            diagnostics.error(location, "Error calling proxy method {{m0}}: " + writer.toString(),
                    model.getProxyMethod());
        }

        MethodDependency implMethod = agent.linkMethod(implRef, location);
        for (int i = 0; i < implRef.parameterCount(); ++i) {
            methodDep.getVariable(i + 1).connect(implMethod.getVariable(i + 1));
        }

        for (int i = 0; i < model.getParameters().size(); ++i) {
            if (model.getParameters().get(i).getKind() == ParameterKind.REFLECT_VALUE) {
                implMethod.getVariable(i + 1).connect(getClassDep.getVariable(0));
            }
        }

        if (implMethod.getResult() != null) {
            implMethod.getResult().connect(methodDep.getResult());
        }
        implMethod.getThrown().connect(methodDep.getThrown());
        implMethod.use();
    }

    private MethodReference buildMethodReference(ProxyParameter param, DependencyType type) {
        if (variants == null) {
            MethodReference ref = new MethodReference(model.getMethod().getClassName(),
                    model.getMethod().getName() + "$defaultUsage", model.getMethod().getSignature());
            model.getUsages().put("", ref);
            return ref;
        }

        int i = 0;
        StringBuilder sb = new StringBuilder();
        ValueType[] signature = new ValueType[model.getParameters().size() + 1];
        for (i = 0; i < variants.length; ++i) {
            if (variants[i] != null) {
                String variant = variants[i][indexes[i]];
                sb.append(variant);
                signature[i] = ValueType.object(variant);
            } else if (param != null && param.getIndex() == i) {
                signature[param.getIndex()] = ValueType.object(type.getName());
                sb.append(type.getName());
            } else {
                signature[i] = model.getParameters().get(i).getType();
            }
        }
        signature[i] = model.getMethod().getReturnType();

        MethodReference implRef = new MethodReference(model.getMethod().getClassName(),
                model.getMethod().getName() + "$proxy" + suffixGenerator++, signature);
        model.getUsages().put(sb.toString(), implRef);

        return implRef;
    }

    private Method getJavaMethod(ClassLoader classLoader, MethodReference ref) throws ReflectiveOperationException {
        Class<?> cls = Class.forName(ref.getClassName(), true, classLoader);
        Class<?>[] parameterTypes = new Class<?>[ref.parameterCount()];
        for (int i = 0; i < parameterTypes.length; ++i) {
            parameterTypes[i] = getJavaType(classLoader, ref.parameterType(i));
        }
        return cls.getDeclaredMethod(ref.getName(), parameterTypes);
    }

    private Class<?> getJavaType(ClassLoader classLoader, ValueType type) throws ReflectiveOperationException {
        if (type instanceof ValueType.Primitive) {
            switch (((ValueType.Primitive) type).getKind()) {
                case BOOLEAN:
                    return boolean.class;
                case BYTE:
                    return byte.class;
                case SHORT:
                    return short.class;
                case CHARACTER:
                    return char.class;
                case INTEGER:
                    return int.class;
                case LONG:
                    return long.class;
                case FLOAT:
                    return float.class;
                case DOUBLE:
                    return double.class;
            }
        } else if (type instanceof ValueType.Array) {
            Class<?> componentType = getJavaType(classLoader, ((ValueType.Array) type).getItemType());
            return Array.newInstance(componentType, 0).getClass();
        } else if (type instanceof ValueType.Object) {
            String className = ((ValueType.Object) type).getClassName();
            return Class.forName(className, true, classLoader);
        } else if (type instanceof ValueType.Void) {
            return void.class;
        }
        throw new AssertionError("Don't know how to map type: " + type);
    }

    private Object getContextParameter(ValueType type) {
        if (type.isObject(Emitter.class)) {
            return emitter;
        } else {
            return null;
        }
    }

    private Variable getParameterVar(ProxyParameter param) {
        Program program = emitter.generator.getProgram();
        Variable var = program.variableAt(param.getIndex() + 1);
        ValueType type = model.getMethod().parameterType(param.getIndex());
        if (type instanceof ValueType.Primitive) {
            switch (((ValueType.Primitive) type).getKind()) {
                case BOOLEAN:
                    var = box(var, Boolean.class, boolean.class);
                    break;
                case BYTE:
                    var = box(var, Byte.class, byte.class);
                    break;
                case SHORT:
                    var = box(var, Short.class, short.class);
                    break;
                case CHARACTER:
                    var = box(var, Character.class, char.class);
                    break;
                case INTEGER:
                    var = box(var, Integer.class, int.class);
                    break;
                case LONG:
                    var = box(var, Long.class, long.class);
                    break;
                case FLOAT:
                    var = box(var, Float.class, float.class);
                    break;
                case DOUBLE:
                    var = box(var, Double.class, double.class);
                    break;
            }
        }
        return var;
    }

    private Variable box(Variable var, Class<?> boxed, Class<?> primitive) {
        Program program = emitter.generator.getProgram();
        BasicBlock block = program.basicBlockAt(0);

        InvokeInstruction insn = new InvokeInstruction();
        insn.setType(InvocationType.SPECIAL);
        insn.setMethod(new MethodReference(boxed, "valueOf", primitive, boxed));
        insn.getArguments().add(var);
        var = program.createVariable();
        insn.setReceiver(var);

        block.getInstructions().add(insn);
        return var;
    }
}
