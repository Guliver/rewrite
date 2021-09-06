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

import org.openrewrite.family.c.CIsoVisitor;
import org.openrewrite.family.c.MethodMatcher;
import org.openrewrite.family.c.marker.JavaSearchResult;
import org.openrewrite.family.c.tree.C;
import org.openrewrite.family.c.tree.CType;
import org.openrewrite.marker.Marker;

import java.util.Set;

import static org.openrewrite.Tree.randomId;

public class DeclaresMethod<P> extends CIsoVisitor<P> {
    @SuppressWarnings("ConstantConditions")
    private static final Marker FOUND_METHOD = new JavaSearchResult(randomId(), null, null);

    private final MethodMatcher methodMatcher;

    public DeclaresMethod(String methodPattern) {
        this(new MethodMatcher(methodPattern));
    }

    public DeclaresMethod(MethodMatcher methodMatcher) {
        this.methodMatcher = methodMatcher;
    }

    @Override
    public C.CompilationUnit visitCompilationUnit(C.CompilationUnit cu, P p) {
        Set<CType.Method> methods = cu.getDeclaredMethods();
        for (CType.Method method : methods) {
            if (methodMatcher.matches(method)) {
                return cu.withMarkers(cu.getMarkers().addIfAbsent(FOUND_METHOD));
            }
        }
        return cu;
    }
}
