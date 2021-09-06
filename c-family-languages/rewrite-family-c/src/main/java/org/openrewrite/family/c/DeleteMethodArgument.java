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
import org.openrewrite.family.c.tree.Expression;
import org.openrewrite.family.c.tree.C;
import org.openrewrite.family.c.tree.Space;
import org.openrewrite.marker.Markers;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.singletonList;
import static org.openrewrite.Tree.randomId;

@Value
@EqualsAndHashCode(callSuper = true)
public class DeleteMethodArgument extends Recipe {
    @Option(displayName = "Method pattern",
            description = "A method pattern, expressed as a pointcut expression, that is used to find matching method invocations.",
            example = "com.yourorg.A foo(int, int)")
    String methodPattern;

    /**
     * A zero-based index that indicates which argument will be removed from the method invocation.
     */
    @Option(displayName = "Argument index",
            description = "A zero-based index that indicates which argument will be removed from the method invocation.",
            example = "0")
    int argumentIndex;

    @Override
    public String getDisplayName() {
        return "Delete method argument";
    }

    @Override
    public String getDescription() {
        return "Delete an argument from method invocations.";
    }

    @Override
    protected CVisitor<ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesMethod<>(methodPattern);
    }

    @Override
    public CVisitor<ExecutionContext> getVisitor() {
        return new DeleteMethodArgumentVisitor(new MethodMatcher(methodPattern));
    }

    private class DeleteMethodArgumentVisitor extends CIsoVisitor<ExecutionContext> {
        private final MethodMatcher methodMatcher;

        public DeleteMethodArgumentVisitor(MethodMatcher methodMatcher) {
            this.methodMatcher = methodMatcher;
        }

        @Override
        public C.MethodInvocation visitMethodInvocation(C.MethodInvocation method, ExecutionContext ctx) {
            C.MethodInvocation m = super.visitMethodInvocation(method, ctx);
            List<Expression> originalArgs = m.getArguments();
            if (methodMatcher.matches(m) && originalArgs.stream()
                    .filter(a -> !(a instanceof C.Empty))
                    .count() >= argumentIndex + 1) {
                List<Expression> args = new ArrayList<>(m.getArguments());

                args.remove(argumentIndex);
                if (args.isEmpty()) {
                    args = singletonList(new C.Empty(randomId(), Space.EMPTY, Markers.EMPTY));
                }

                m = m.withArguments(args);
            }
            return m;
        }
    }
}
