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

import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.family.c.search.UsesMethod;
import org.openrewrite.family.c.tree.C;
import org.openrewrite.family.c.tree.CRightPadded;
import org.openrewrite.family.c.tree.Expression;
import org.openrewrite.family.c.tree.Space;
import org.openrewrite.internal.lang.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Value
@EqualsAndHashCode(callSuper = true)
public class ReorderMethodArguments extends Recipe {
    @Option(displayName = "Method pattern",
            description = "A method pattern, expressed as a pointcut expression, that is used to find matching method invocations.",
            example = "com.yourorg.A foo(String, Integer, Integer)")
    String methodPattern;

    /**
     * An array of parameter names that indicates the new order in which those arguments should be arranged.
     */
    @Option(displayName = "New parameter names",
            description = "An array of parameter names that indicates the new order in which those arguments should be arranged.",
            example = "[foo, bar, baz]")
    String[] newParameterNames;

    /**
     * If the original method signature is not type-attributed, this is an optional list that indicates the original order
     * in which the arguments were arranged.
     */
    @Option(displayName = "Old parameter names",
            description = "If the original method signature is not type-attributed, this is an optional list that indicates the original order in which the arguments were arranged.",
            required = false,
            example = "[baz, bar, foo]")
    @Nullable
    String[] oldParameterNames;

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
    }

    @Override
    public String getDisplayName() {
        return "Reorder method arguments";
    }

    @Override
    public String getDescription() {
        return "Reorder method arguments into the specified order.";
    }

    @Override
    protected CVisitor<ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesMethod<>(methodPattern);
    }

    @Override
    public CVisitor<ExecutionContext> getVisitor() {
        return new ReorderMethodArgumentsVisitor(new MethodMatcher(methodPattern));
    }

    private class ReorderMethodArgumentsVisitor extends CIsoVisitor<ExecutionContext> {
        private final MethodMatcher methodMatcher;

        private ReorderMethodArgumentsVisitor(MethodMatcher methodMatcher) {
            this.methodMatcher = methodMatcher;
        }

        @Override
        public C.MethodInvocation visitMethodInvocation(C.MethodInvocation method, ExecutionContext ctx) {
            C.MethodInvocation m = super.visitMethodInvocation(method, ctx);

            if (methodMatcher.matches(m) && m.getType() != null) {
                @SuppressWarnings("ConstantConditions") List<String> paramNames =
                        oldParameterNames == null || oldParameterNames.length == 0 ?
                                m.getType().getParamNames() :
                                Arrays.asList(oldParameterNames);

                if (paramNames == null) {
                    throw new IllegalStateException("There is no source attachment for method " + m.getType().getDeclaringType().getFullyQualifiedName() +
                            "." + m.getSimpleName() + "(..). Provide a reference for original parameter names by calling setOriginalParamNames(..)");
                }

                List<CRightPadded<Expression>> originalArgs = m.getPadding().getArguments().getPadding().getElements();

                int resolvedParamCount = m.getType().getResolvedSignature() == null ? originalArgs.size() :
                        m.getType().getResolvedSignature().getParamTypes().size();

                int i = 0;
                List<CRightPadded<Expression>> reordered = new ArrayList<>(originalArgs.size());
                List<Space> formattings = new ArrayList<>(originalArgs.size());
                List<Space> rightFormattings = new ArrayList<>(originalArgs.size());

                for (String name : newParameterNames) {
                    int fromPos = paramNames.indexOf(name);
                    if (originalArgs.size() > resolvedParamCount && fromPos == resolvedParamCount - 1) {
                        // this is a varargs argument
                        List<CRightPadded<Expression>> varargs = originalArgs.subList(fromPos, originalArgs.size());
                        reordered.addAll(varargs);
                        for (CRightPadded<Expression> exp : originalArgs.subList(i, (i++) + varargs.size())) {
                            formattings.add(exp.getElement().getPrefix());
                            rightFormattings.add(exp.getAfter());
                        }
                    } else if (fromPos >= 0 && originalArgs.size() > fromPos) {
                        reordered.add(originalArgs.get(fromPos));
                        formattings.add(originalArgs.get(i).getElement().getPrefix());
                        rightFormattings.add(originalArgs.get(i++).getAfter());
                    }
                }

                boolean changed = false;
                i = 0;
                for (CRightPadded<Expression> expression : reordered) {
                    final int index = i;
                    reordered.set(i, expression
                            .map(e -> e.withPrefix(formattings.get(index)))
                            .withAfter(rightFormattings.get(index)));
                    if (reordered.get(i) != originalArgs.get(i)) {
                        changed = true;
                    }
                    i++;
                }

                if (changed) {
                    m = m.getPadding().withArguments(m.getPadding().getArguments().getPadding().withElements(reordered));
                }
            }
            return m;
        }
    }
}
