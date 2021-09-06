/*
 * Copyright 2021 the original author or authors.
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
package org.openrewrite.family.c.search;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.family.c.CIsoVisitor;
import org.openrewrite.family.c.MethodMatcher;
import org.openrewrite.family.c.marker.JavaSearchResult;
import org.openrewrite.family.c.tree.C;

@Value
@EqualsAndHashCode(callSuper = true)
public class ResultOfMethodCallIgnored extends Recipe {
    /**
     * A method pattern, expressed as a pointcut expression, that is used to find matching method invocations.
     * See {@link MethodMatcher} for details on the expression's syntax.
     */
    @Option(displayName = "Method pattern",
            description = "A method pattern, expressed as a pointcut expression, that is used to find matching method invocations.",
            example = "java.io.File mkdir*()")
    String methodPattern;

    @Override
    public String getDisplayName() {
        return "Result of method call ignored";
    }

    @Override
    public String getDescription() {
        return "Find locations where the result of the method call is being ignored.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        MethodMatcher methodMatcher = new MethodMatcher(methodPattern);
        return new CIsoVisitor<ExecutionContext>() {
            @Override
            public C.MethodInvocation visitMethodInvocation(C.MethodInvocation method, ExecutionContext executionContext) {
                C.MethodInvocation m = super.visitMethodInvocation(method, executionContext);
                if (methodMatcher.matches(method)) {
                    if (getCursor().dropParentUntil(C.class::isInstance).getValue() instanceof C.Block) {
                        m = m.withMarkers(m.getMarkers().addIfAbsent(new JavaSearchResult(ResultOfMethodCallIgnored.this)));
                    }
                }
                return m;
            }
        };
    }
}
