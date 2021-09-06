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
package org.openrewrite.java.cleanup;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.J;

import java.util.Collections;
import java.util.Set;

public class RemoveExtraSemicolons extends Recipe {

    @Override
    public String getDisplayName() {
        return "Remove extra semicolons";
    }

    @Override
    public String getDescription() {
        return "Optional semicolons at the end of try-with-resources are also removed.";
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("RSPEC-2959");
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    protected JavaVisitor<ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitEmpty(J.Empty empty, ExecutionContext ctx) {
                if (getCursor().dropParentUntil(J.class::isInstance).getValue() instanceof J.Block) {
                    return null;
                }
                return super.visitEmpty(empty, ctx);
            }

            @Override
            public J visitTry(J.Try tryable, ExecutionContext ctx) {
                return tryable.withResources(ListUtils.map(tryable.getResources(), r -> r.withTerminatedWithSemicolon(false)))
                        .withBody((J.Block) super.visit(tryable.getBody(), ctx))
                        .withCatches(ListUtils.map(tryable.getCatches(), c -> (J.Try.Catch) super.visit(c, ctx)))
                        .withFinally((J.Block) super.visit(tryable.getFinally(), ctx));
            }
        };
    }
}
