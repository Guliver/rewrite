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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.family.c.CIsoVisitor;
import org.openrewrite.family.c.CVisitor;
import org.openrewrite.family.c.marker.JavaSearchResult;
import org.openrewrite.family.c.tree.Expression;
import org.openrewrite.family.c.tree.C;
import org.openrewrite.family.c.tree.CType;

public class FindMissingTypes extends Recipe {

    @Override
    public String getDisplayName() {
        return "Find missing type information on Java ASTs";
    }

    @Override
    public String getDescription() {
        return "This is a diagnostic recipe to highlight where ASTs are missing type attribution information.";
    }

    @Override
    protected CVisitor<ExecutionContext> getVisitor() {
        return new CIsoVisitor<ExecutionContext>() {
            @Override
            public C.MethodInvocation visitMethodInvocation(C.MethodInvocation method, ExecutionContext executionContext) {
                CType.Method type = method.getType();
                Expression select = method.getSelect();
                if(select != null) {
                    if (type == null) {
                        return method.withSelect(select.withMarkers(select.getMarkers().addIfAbsent(new JavaSearchResult(FindMissingTypes.this, "type is `null`"))));
                    } else if (type.getGenericSignature() == null) {
                        return method.withSelect(select.withMarkers(select.getMarkers().addIfAbsent(new JavaSearchResult(FindMissingTypes.this, "generic signature is `null`"))));
                    } else if (!type.getName().equals(method.getSimpleName())) {
                        return method.withSelect(select.withMarkers(select.getMarkers().addIfAbsent(new JavaSearchResult(FindMissingTypes.this, "type information has a different method name '" + type.getName() + "'"))));
                    }
                }
                return super.visitMethodInvocation(method, executionContext);
            }
        };
    }
}
