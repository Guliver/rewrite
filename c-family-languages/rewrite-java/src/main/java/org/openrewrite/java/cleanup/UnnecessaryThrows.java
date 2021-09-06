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
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.*;

import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

public class UnnecessaryThrows extends Recipe {

    @Override
    public String getDisplayName() {
        return "Unnecessary throws";
    }

    @Override
    public String getDescription() {
        return "Remove unnecessary `throws` declarations.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                if (m.getThrows() != null && !m.isAbstract()) {
                    Set<CType.FullyQualified> unusedThrows = new TreeSet<>(Comparator.comparing(CType.FullyQualified::getFullyQualifiedName));
                    for (NameTree nameTree : m.getThrows()) {
                        if (!TypeUtils.isAssignableTo(CType.Class.build("java.lang.RuntimeException"), nameTree.getType())) {
                            unusedThrows.add(TypeUtils.asFullyQualified(nameTree.getType()));
                        }
                    }

                    new JavaIsoVisitor<ExecutionContext>() {
                        @Nullable
                        @Override
                        public J visit(@Nullable Tree tree, ExecutionContext ctx) {
                            if (unusedThrows.isEmpty()) {
                                return (J) tree;
                            }
                            return super.visit(tree, ctx);
                        }

                        @Override
                        public J.Try.Resource visitTryResource(J.Try.Resource tryResource, ExecutionContext executionContext) {
                            TypedTree resource = tryResource.getVariableDeclarations();

                            CType.FullyQualified resourceType = TypeUtils.asFullyQualified(resource.getType());
                            if (resourceType != null) {
                                if (TypeUtils.isAssignableTo(CType.Class.build("java.io.Closeable"), resourceType)) {
                                    unusedThrows.remove(CType.Class.build("java.io.IOException"));
                                } else if (TypeUtils.isAssignableTo(CType.Class.build("java.lang.AutoCloseable"), resourceType)) {
                                    unusedThrows.remove(CType.Class.build("java.lang.Exception"));
                                }
                            }

                            return super.visitTryResource(tryResource, executionContext);
                        }

                        @Override
                        public J.Throw visitThrow(J.Throw thrown, ExecutionContext executionContext) {
                            CType.FullyQualified type = TypeUtils.asFullyQualified(thrown.getException().getType());
                            if (type != null) {
                                unusedThrows.removeIf(t -> TypeUtils.isAssignableTo(t, type));
                            }
                            return thrown;
                        }

                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                            removeThrownTypes(method.getType());
                            return super.visitMethodInvocation(method, ctx);
                        }

                        @Override
                        public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                            removeThrownTypes(newClass.getConstructorType());
                            return super.visitNewClass(newClass, ctx);
                        }

                        private void removeThrownTypes(@Nullable CType.Method type) {
                            if (type != null) {
                                for (CType.FullyQualified thrownException : type.getThrownExceptions()) {
                                    unusedThrows.removeIf(t -> TypeUtils.isAssignableTo(t, thrownException));
                                }
                            }
                        }
                    }.visit(m, ctx);

                    if (!unusedThrows.isEmpty()) {
                        m = m.withThrows(ListUtils.map(m.getThrows(), t -> {
                            CType.FullyQualified type = TypeUtils.asFullyQualified(t.getType());
                            if (type != null && unusedThrows.contains(type)) {
                                maybeRemoveImport(type);
                                return null;
                            }
                            return t;
                        }));
                    }
                }

                return m;
            }
        };
    }
}
