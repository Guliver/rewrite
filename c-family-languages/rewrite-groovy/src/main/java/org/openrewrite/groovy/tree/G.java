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
package org.openrewrite.groovy.tree;

import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import org.openrewrite.Tree;
import org.openrewrite.TreePrinter;
import org.openrewrite.TreeVisitor;
import org.openrewrite.groovy.GroovyPrinter;
import org.openrewrite.groovy.GroovyVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.internal.TypeCache;
import org.openrewrite.java.search.FindTypes;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public interface G extends J {
    @SuppressWarnings("unchecked")
    @Override
    default <R extends Tree, P> R accept(TreeVisitor<R, P> v, P p) {
        return (R) acceptGroovy((GroovyVisitor<P>) v, p);
    }

    @Override
    default <P> boolean isAcceptable(TreeVisitor<?, P> v, P p) {
        return v instanceof GroovyVisitor;
    }

    @Nullable
    default <P> G acceptGroovy(GroovyVisitor<P> v, P p) {
        return (G) v.defaultValue(this, p);
    }

    Space getPrefix();

    default List<Comment> getComments() {
        return getPrefix().getComments();
    }

    default <P> String print(TreePrinter<P> printer, P p) {
        return new GroovyPrinter<>(printer).print(this, p);
    }

    @Override
    default <P> String print(P p) {
        return print(TreePrinter.identity(), p);
    }

    @ToString
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    @RequiredArgsConstructor
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    final class CompilationUnit implements G, JavaSourceFile {
        @Nullable
        @NonFinal
        transient WeakReference<TypeCache> typesInUse;

        @Nullable
        @NonFinal
        transient WeakReference<Padding> padding;

        @EqualsAndHashCode.Include
        @With
        @Getter
        UUID id;

        @With
        @Getter
        Space prefix;

        @With
        @Getter
        Markers markers;

        @With
        @Getter
        Path sourcePath;

        @Nullable
        JRightPadded<Package> packageDeclaration;

        @Override
        @Nullable
        public Package getPackageDeclaration() {
            return packageDeclaration == null ? null : packageDeclaration.getElement();
        }

        @Override
        public G.CompilationUnit withPackageDeclaration(Package packageDeclaration) {
            return getPadding().withPackageDeclaration(JRightPadded.withElement(this.packageDeclaration, packageDeclaration));
        }

        List<JRightPadded<Statement>> statements;

        public List<Statement> getStatements() {
            return JRightPadded.getElements(statements);
        }

        public G.CompilationUnit withStatements(List<Statement> statements) {
            return getPadding().withStatements(JRightPadded.withElements(this.statements, statements));
        }

        @With
        @Getter
        Space eof;

        @Override
        public List<Import> getImports() {
            return statements.stream()
                    .map(JRightPadded::getElement)
                    .filter(J.Import.class::isInstance)
                    .map(J.Import.class::cast)
                    .collect(Collectors.toList());
        }

        /**
         * This will move all imports to the front of every other statement in the file.
         * If the result is no change, then the original instance is returned.
         *
         * @param imports The imports to use.
         * @return This compilation unit with new imports.
         */
        @Override
        public G.CompilationUnit withImports(List<Import> imports) {
//            List<Statement> after = ListUtils.concatAll(
//                    imports.stream()
//                            .map(s -> (Statement) s)
//                            .collect(Collectors.toList()),
//                    statements.stream()
//                            .map(JRightPadded::getElement)
//                            .filter(s -> !(s instanceof Import))
//                            .collect(Collectors.toList()));
//
//            if (after.size() != statements.size()) {
//                return padding.withStatements(after);
//            }
//
//            for (int i = 0; i < statements.size(); i++) {
//                Statement statement = statements.get(i);
//                if (after.get(i) != statement) {
//                    return withStatements(after);
//                }
//            }

            // TODO implement me!
            return this;
        }

        @Override
        public List<ClassDeclaration> getClasses() {
            return statements.stream()
                    .map(JRightPadded::getElement)
                    .filter(J.ClassDeclaration.class::isInstance)
                    .map(J.ClassDeclaration.class::cast)
                    .collect(Collectors.toList());
        }

        /**
         * This will move all classes to after last import. Every other statement which is neither
         * an import or class declaration will appear last.
         * <p>
         * If the result is no change, then the original instance is returned.
         *
         * @param classes The classes to use.
         * @return This compilation unit with new classes.
         */
        @Override
        public G.CompilationUnit withClasses(List<ClassDeclaration> classes) {
            // TODO implement me!
            return this;
        }

        @Override
        public <P> J acceptJava(JavaVisitor<P> v, P p) {
            return v.visitCompilationUnit(this, p);
        }

        @Override
        public <P> G acceptGroovy(GroovyVisitor<P> v, P p) {
            return v.visitCompilationUnit(this, p);
        }

        @Override
        public Set<NameTree> findType(String clazz) {
            return FindTypes.find(this, clazz);
        }

        @Override
        public Set<JavaType> getTypesInUse() {
            TypeCache cache;
//            if (this.typesInUse == null) {
//                cache = TypeCache.build(this);
//                this.typesInUse = new WeakReference<>(cache);
//            } else {
//                cache = this.typesInUse.get();
//                if (cache == null || cache.t != this) {
//                    cache = new TypeCache(this, FindAllUsedTypes.findAll(this));
//                    this.typesInUse = new WeakReference<>(cache);
//                }
//            }
//            return cache.typesInUse;
            return null;
        }

        @RequiredArgsConstructor
        private static class TypeCache {
            private final G.CompilationUnit t;
            private final Set<JavaType> typesInUse;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Padding getPadding() {
            Padding p;
            if (this.padding == null) {
                p = new Padding(this);
                this.padding = new WeakReference<>(p);
            } else {
                p = this.padding.get();
                if (p == null || p.t != this) {
                    p = new Padding(this);
                    this.padding = new WeakReference<>(p);
                }
            }
            return p;
        }

        @RequiredArgsConstructor
        public static class Padding implements JavaSourceFile.Padding {
            private final G.CompilationUnit t;

            @Nullable
            public JRightPadded<Package> getPackageDeclaration() {
                return t.packageDeclaration;
            }

            public G.CompilationUnit withPackageDeclaration(@Nullable JRightPadded<Package> packageDeclaration) {
                return t.packageDeclaration == packageDeclaration ? t : new G.CompilationUnit(t.id, t.prefix, t.markers, t.sourcePath, packageDeclaration, t.statements, t.eof);
            }

            public List<JRightPadded<Import>> getImports() {
                //noinspection unchecked
                return t.statements.stream()
                        .filter(s -> s.getElement() instanceof J.Import)
                        .map(s -> (JRightPadded<J.Import>) (Object) s)
                        .collect(Collectors.toList());
            }

            public G.CompilationUnit withImports(List<JRightPadded<Import>> imports) {
                // TODO implement me!
                return t;
//                return t.imports == imports ? t : new G.CompilationUnit(t.id, t.prefix, t.markers, t.sourcePath, t.packageDeclaration, imports, t.classes, t.eof);
            }

            public List<JRightPadded<Statement>> getStatements() {
                return t.statements;
            }

            public G.CompilationUnit withStatements(List<JRightPadded<Statement>> statements) {
                return t.statements == statements ? t : new G.CompilationUnit(t.id, t.prefix, t.markers, t.sourcePath, t.packageDeclaration, statements, t.eof);
            }
        }
    }
}
