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
package org.teavm.flavour.expr;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import org.teavm.flavour.expr.ast.Expr;
import org.teavm.flavour.expr.type.ClassPathClassDescriberRepository;
import org.teavm.flavour.expr.type.ValueType;

/**
 *
 * @author Alexey Andreev
 */
public class InterpretingEvaluatorBuilder implements EvaluatorBuilder {
    @Override
    public <F, V> Evaluator<F, V> build(Class<F> functionType, Class<V> variablesType, String exprString) {
        if (!functionType.isInterface()) {
            throw new IllegalArgumentException("Function type must be an interface");
        }
        Method[] functionMethods = functionType.getDeclaredMethods();
        if (functionMethods.length != 1) {
            throw new IllegalArgumentException("Function type must have exactly one method");
        }

        if (!variablesType.isInterface()) {
            throw new IllegalArgumentException("Variables type must be an interface");
        }
        Method[] variableMethods = variablesType.getDeclaredMethods();
        Map<String, Type> variableTypes = new HashMap<>();
        Map<Method, String> methodToVariableMap = new HashMap<>();
        for (Method method : variableMethods) {
            if (!method.getReturnType().equals(void.class)) {
                throw new IllegalArgumentException("Method " + method + " does not return void");
            }
            Type[] parameters = method.getGenericParameterTypes();
            if (parameters.length != 1) {
                throw new IllegalArgumentException("Method " + method + " does not take one parameter");
            }
            methodToVariableMap.put(method, method.getName());
            variableTypes.put(method.getName(), parameters[0]);
        }

        Parser parser = new Parser(new ClassLoaderClassSet(ClassLoader.getSystemClassLoader()));
        Expr<Void> expr = parser.parse(exprString);
        if (!parser.getSyntaxErrors().isEmpty()) {
            throw new RuntimeException("Syntax errors found in expression " + exprString);
        }

        ClassPathClassDescriberRepository classes = new ClassPathClassDescriberRepository();
        Compiler compiler = new Compiler(classes, new ScopeImpl(classes, variableTypes));
        TypedPlan typedPlan = compiler.compile(expr);
        if (!compiler.wasSuccessful()) {
            throw new RuntimeException("Type errors found in expression " + exprString);
        }

        Interpreter interpreter = new Interpreter(typedPlan.plan);
        @SuppressWarnings("unchecked")
        F function = (F)Proxy.newProxyInstance(ClassLoader.getSystemClassLoader(), new Class<?>[] { functionType },
                new FunctionProxy(interpreter));
        @SuppressWarnings("unchecked")
        V variables = (V)Proxy.newProxyInstance(ClassLoader.getSystemClassLoader(), new Class<?>[] { variablesType },
                new VariablesProxy(interpreter, methodToVariableMap));

        return new Evaluator<>(function, variables);
    }

    class ScopeImpl implements Scope {
        private ClassPathClassDescriberRepository classes;
        private Map<String, Type> variables;

        public ScopeImpl(ClassPathClassDescriberRepository classes, Map<String, Type> variables) {
            this.classes = classes;
            this.variables = variables;
        }

        @Override
        public ValueType variableType(String variableName) {
            Type type = variables.get(variableName);
            return type != null ? classes.convertGenericType(type) : null;
        }
    }

    class FunctionProxy implements InvocationHandler {
        private Interpreter interpreter;

        public FunctionProxy(Interpreter interpreter) {
            this.interpreter = interpreter;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            return interpreter.interpret();
        }
    }

    class VariablesProxy implements InvocationHandler {
        private Interpreter interpreter;
        private Map<Method, String> variables;

        public VariablesProxy(Interpreter interpreter, Map<Method, String> variables) {
            this.interpreter = interpreter;
            this.variables = variables;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String varName = variables.get(method);
            interpreter.getVariables().put(varName, args[0]);
            return null;
        }
    }
}