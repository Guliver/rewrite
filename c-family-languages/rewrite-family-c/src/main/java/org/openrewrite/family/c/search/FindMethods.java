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
package org.openrewrite.family.c.search;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.family.c.CIsoVisitor;
import org.openrewrite.family.c.CVisitor;
import org.openrewrite.family.c.MethodMatcher;
import org.openrewrite.family.c.marker.JavaSearchResult;
import org.openrewrite.family.c.tree.C;

import java.util.HashSet;
import java.util.Set;

/**
 * Finds matching method invocations.
 */
@EqualsAndHashCode(callSuper = true)
@Value
public class FindMethods extends Recipe {

    /**
     * A method pattern, expressed as a pointcut expression, that is used to find matching method invocations.
     * See {@link MethodMatcher} for details on the expression's syntax.
     */
    @Option(displayName = "Method pattern",
            description = "A method pattern, expressed as a pointcut expression, that is used to find matching method invocations.",
            example = "java.util.List add(..)")
    String methodPattern;

    @Option(displayName = "Match on overrides",
            description = "When enabled, find methods that are overloads of the method pattern.",
            required = false)
    @Nullable
    Boolean matchOverrides;

    @Override
    public String getDisplayName() {
        return "Find methods";
    }

    @Override
    public String getDescription() {
        return "Find methods by pattern.";
    }

    @Override
    protected CVisitor<ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesMethod<>(methodPattern, Boolean.TRUE.equals(matchOverrides));
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        MethodMatcher methodMatcher = new MethodMatcher(methodPattern, Boolean.TRUE.equals(matchOverrides));
        return new CIsoVisitor<ExecutionContext>() {
            @Override
            public C.MethodInvocation visitMethodInvocation(C.MethodInvocation method, ExecutionContext ctx) {
                C.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                if (methodMatcher.matches(method)) {
                    m = m.withMarkers(m.getMarkers().addIfAbsent(new JavaSearchResult(FindMethods.this)));
                }
                return m;
            }

            @Override
            public C.MemberReference visitMemberReference(C.MemberReference memberRef, ExecutionContext ctx) {
                C.MemberReference m = super.visitMemberReference(memberRef, ctx);
                if (methodMatcher.matches(m.getReferenceType())) {
                    m = m.withReference(m.getReference().withMarkers(m.getReference().getMarkers().addIfAbsent(new JavaSearchResult(FindMethods.this))));
                }
                return m;
            }
        };
    }

    /**
     * @param c             The subtree to search.
     * @param methodPattern A method pattern. See {@link MethodMatcher} for details about this syntax.
     * @return A set of {@link C.MethodInvocation} and {@link C.MemberReference} representing calls to this method.
     */
    public static Set<C> find(C c, String methodPattern) {
        MethodMatcher methodMatcher = new MethodMatcher(methodPattern);
        CIsoVisitor<Set<C>> findVisitor = new CIsoVisitor<Set<C>>() {
            @Override
            public C.MethodInvocation visitMethodInvocation(C.MethodInvocation method, Set<C> ms) {
                if (methodMatcher.matches(method)) {
                    ms.add(method);
                }
                return super.visitMethodInvocation(method, ms);
            }

            @Override
            public C.MemberReference visitMemberReference(C.MemberReference memberRef, Set<C> ms) {
                if (methodMatcher.matches(memberRef.getReferenceType())) {
                    ms.add(memberRef);
                }
                return super.visitMemberReference(memberRef, ms);
            }
        };

        Set<C> ms = new HashSet<>();
        findVisitor.visit(c, ms);
        return ms;
    }
}
