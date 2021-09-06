/*
 * Copyright 2020 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.family.c;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.family.c.search.UsesMethod;
import org.openrewrite.family.c.tree.Flag;
import org.openrewrite.family.c.tree.C;
import org.openrewrite.family.c.tree.CType;
import org.openrewrite.family.c.tree.Space;
import org.openrewrite.marker.Markers;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.openrewrite.Tree.randomId;

@Value
@EqualsAndHashCode(callSuper = true)
public class ChangeMethodTargetToVariable extends Recipe {
    @Option(displayName = "Method pattern",
            description = "A method pattern, expressed as a pointcut expression, that is used to find matching method invocations.",
            example = "org.mycorp.A method(..)")
    String methodPattern;

    @Option(displayName = "Variable name",
            description = "Name of variable to use as target for the modified method invocation.",
            example = "foo")
    String variableName;

    @Option(displayName = "Variable type",
            description = "Type attribution to use for the return type of the modified method invocation.",
            example = "java.lang.String")
    String variableType;

    @Override
    public String getDisplayName() {
        return "Change method target to variable";
    }

    @Override
    public String getDescription() {
        return "Change method invocations to method calls on a variable.";
    }

    @Override
    public CVisitor<ExecutionContext> getVisitor() {
        return new ChangeMethodTargetToVariableVisitor(new MethodMatcher(methodPattern), CType.Class.build(variableType));
    }

    @Override
    protected CVisitor<ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesMethod<>(methodPattern);
    }

    private class ChangeMethodTargetToVariableVisitor extends CIsoVisitor<ExecutionContext> {
        private final MethodMatcher methodMatcher;
        private final CType.Class variableType;

        private ChangeMethodTargetToVariableVisitor(MethodMatcher methodMatcher, CType.Class variableType) {
            this.methodMatcher = methodMatcher;
            this.variableType = variableType;
        }

        @Override
        public C.MethodInvocation visitMethodInvocation(C.MethodInvocation method, ExecutionContext ctx) {
            C.MethodInvocation m = super.visitMethodInvocation(method, ctx);
            if (methodMatcher.matches(m)) {

                CType.Method methodType = null;
                if (m.getType() != null) {
                    maybeRemoveImport(m.getType().getDeclaringType());

                    Set<Flag> flags = new LinkedHashSet<>(m.getType().getFlags());
                    flags.remove(Flag.Static);
                    methodType = m.getType().withDeclaringType(this.variableType).withFlags(flags);
                }

                m = m.withSelect(C.Identifier.build(randomId(),
                        m.getSelect() == null ?
                                Space.EMPTY :
                                m.getSelect().getPrefix(),
                        Markers.EMPTY,
                        variableName,
                        this.variableType)
                ).withType(methodType);
            }
            return m;
        }
    }
}
