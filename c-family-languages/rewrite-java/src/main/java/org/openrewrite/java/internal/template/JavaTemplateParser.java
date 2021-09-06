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
package org.openrewrite.java.internal.template;

import org.intellij.lang.annotations.Language;
import org.openrewrite.Cursor;
import org.openrewrite.RandomizeIdVisitor;
import org.openrewrite.family.c.tree.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.PropertyPlaceholderHelper;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static java.util.Collections.singletonList;

public class JavaTemplateParser {
    private static final PropertyPlaceholderHelper placeholderHelper = new PropertyPlaceholderHelper("#{", "}", null);

    private final Object templateCacheLock = new Object();

    private static final Map<String, List<? extends C>> templateCache = new LinkedHashMap<String, List<? extends C>>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry eldest) {
            return size() > 10_000;
        }
    };

    private static final String PACKAGE_STUB = "package #{}; class $Template {}";
    private static final String PARAMETER_STUB = "abstract class $Template { abstract void $template(#{}); }";
    private static final String LAMBDA_PARAMETER_STUB = "class $Template { { Object o = (#{}) -> {}; } }";
    private static final String EXPRESSION_STUB = "class $Template { { Object o = #{} } }";
    private static final String EXTENDS_STUB = "class $Template extends #{} {}";
    private static final String IMPLEMENTS_STUB = "class $Template implements #{} {}";
    private static final String THROWS_STUB = "abstract class $Template { abstract void $template() throws #{}; }";
    private static final String TYPE_PARAMS_STUB = "class $Template<#{}> {}";

    @Language("java")
    private static final String SUBSTITUTED_ANNOTATION = "@java.lang.annotation.Documented public @interface SubAnnotation { int value(); }";

    private final Supplier<JavaParser> parser;
    private final Consumer<String> onAfterVariableSubstitution;
    private final Consumer<String> onBeforeParseTemplate;
    private final Set<String> imports;
    private final BlockStatementTemplateGenerator statementTemplateGenerator;
    private final AnnotationTemplateGenerator annotationTemplateGenerator;

    public JavaTemplateParser(Supplier<JavaParser> parser, Consumer<String> onAfterVariableSubstitution,
                              Consumer<String> onBeforeParseTemplate, Set<String> imports) {
        this.parser = parser;
        this.onAfterVariableSubstitution = onAfterVariableSubstitution;
        this.onBeforeParseTemplate = onBeforeParseTemplate;
        this.imports = imports;
        this.statementTemplateGenerator = new BlockStatementTemplateGenerator(imports);
        this.annotationTemplateGenerator = new AnnotationTemplateGenerator(imports);
    }

    public List<Statement> parseParameters(String template) {
        @Language("java") String stub = addImports(substitute(PARAMETER_STUB, template));
        onBeforeParseTemplate.accept(stub);
        return cache(stub, () -> {
            J.CompilationUnit cu = compileTemplate(stub);
            J.MethodDeclaration m = (J.MethodDeclaration) cu.getClasses().get(0).getBody().getStatements().get(0);
            return m.getParameters();
        });
    }

    public J.Lambda.Parameters parseLambdaParameters(String template) {
        @Language("java") String stub = addImports(substitute(LAMBDA_PARAMETER_STUB, template));
        onBeforeParseTemplate.accept(stub);

        return (J.Lambda.Parameters) cache(stub, () -> {
            J.CompilationUnit cu = compileTemplate(stub);
            J.Block b = (J.Block) cu.getClasses().get(0).getBody().getStatements().get(0);
            J.VariableDeclarations v = (J.VariableDeclarations) b.getStatements().get(0);
            J.Lambda l = (J.Lambda) v.getVariables().get(0).getInitializer();
            assert l != null;
            return singletonList(l.getParameters());
        }).get(0);
    }

    public C parseExpression(String template) {
        @Language("java") String stub = addImports(substitute(EXPRESSION_STUB, template));
        onBeforeParseTemplate.accept(stub);

        return cache(stub, () -> {
            J.CompilationUnit cu = compileTemplate(stub);
            C.Block b = (J.Block) cu.getClasses().get(0).getBody().getStatements().get(0);
            C.VariableDeclarations v = (C.VariableDeclarations) b.getStatements().get(0);
            return singletonList(v.getVariables().get(0).getInitializer());
        }).get(0);
    }

    public TypeTree parseExtends(String template) {
        @Language("java") String stub = addImports(substitute(EXTENDS_STUB, template));
        onBeforeParseTemplate.accept(stub);

        return (TypeTree) cache(stub, () -> {
            J.CompilationUnit cu = compileTemplate(stub);
            TypeTree anExtends = cu.getClasses().get(0).getExtends();
            assert anExtends != null;
            return singletonList(anExtends);
        }).get(0);
    }

    public List<TypeTree> parseImplements(String template) {
        @Language("java") String stub = addImports(substitute(IMPLEMENTS_STUB, template));
        onBeforeParseTemplate.accept(stub);
        return cache(stub, () -> {
            J.CompilationUnit cu = compileTemplate(stub);
            List<TypeTree> anImplements = cu.getClasses().get(0).getImplements();
            assert anImplements != null;
            return anImplements;
        });
    }

    public List<NameTree> parseThrows(String template) {
        @Language("java") String stub = addImports(substitute(THROWS_STUB, template));
        onBeforeParseTemplate.accept(stub);
        return cache(stub, () -> {
            J.CompilationUnit cu = compileTemplate(stub);
            J.MethodDeclaration m = (J.MethodDeclaration) cu.getClasses().get(0).getBody().getStatements().get(0);
            List<NameTree> aThrows = m.getThrows();
            assert aThrows != null;
            return aThrows;
        });
    }

    public List<C.TypeParameter> parseTypeParameters(String template) {
        @Language("java") String stub = addImports(substitute(TYPE_PARAMS_STUB, template));
        onBeforeParseTemplate.accept(stub);
        return cache(stub, () -> {
            J.CompilationUnit cu = compileTemplate(stub);
            List<C.TypeParameter> tps = cu.getClasses().get(0).getTypeParameters();
            assert tps != null;
            return tps;
        });
    }

    public List<Statement> parseBlockStatements(Cursor cursor, String template, Space.Location location) {
        @Language("java") String stub = statementTemplateGenerator.template(cursor, template, location);
        onBeforeParseTemplate.accept(stub);
        return cache(stub, () -> {
            J.CompilationUnit cu = compileTemplate(stub);
            return statementTemplateGenerator.listTemplatedStatements(cu);
        });
    }

    public C.MethodInvocation parseMethodArguments(Cursor cursor, String template, Space.Location location) {
        C.MethodInvocation method = cursor.getValue();
        String methodWithReplacementArgs = J.printTrimmed(method.withArguments(Collections.emptyList()))
                .replaceAll("\\)$", template + ");");
        @Language("java") String stub = statementTemplateGenerator.template(cursor, methodWithReplacementArgs, location);
        onBeforeParseTemplate.accept(stub);
        List<C> invocations = cache(stub, () -> {
            J.CompilationUnit cu = compileTemplate(stub);
            C.MethodInvocation replaced = (J.MethodInvocation) statementTemplateGenerator.listTemplatedStatements(cu).get(0);
            return Collections.singletonList(replaced);
        });
        return (C.MethodInvocation) invocations.get(0);
    }

    public List<C.Annotation> parseAnnotations(Cursor cursor, String template) {
        @Language("java") String stub = annotationTemplateGenerator.template(cursor, template);
        onBeforeParseTemplate.accept(stub);
        return cache(stub, () -> {
            J.CompilationUnit cu = compileTemplate(stub);
            return annotationTemplateGenerator.listAnnotations(cu);
        });
    }

    public Expression parsePackage(String template) {
        @Language("java") String stub = substitute(PACKAGE_STUB, template);
        onBeforeParseTemplate.accept(stub);

        return (Expression) cache(stub, () -> {
            J.CompilationUnit cu = compileTemplate(stub);
            @SuppressWarnings("ConstantConditions") Expression expression = cu.getPackageDeclaration()
                    .getExpression();
            return singletonList(expression);
        }).get(0);
    }

    private String substitute(String stub, String template) {
        String beforeParse = placeholderHelper.replacePlaceholders(stub, k -> template);
        onAfterVariableSubstitution.accept(beforeParse);
        return beforeParse;
    }

    private String addImports(String stub) {
        if (!imports.isEmpty()) {
            StringBuilder withImports = new StringBuilder();
            for (String anImport : imports) {
                withImports.append(anImport);
            }
            withImports.append(stub);
            return withImports.toString();
        }
        return stub;
    }

    private J.CompilationUnit compileTemplate(@Language("java") String stub) {
        return stub.contains("@SubAnnotation") ?
                parser.get().reset().parse(stub, SUBSTITUTED_ANNOTATION).get(0) :
                parser.get().reset().parse(stub).get(0);
    }

    @SuppressWarnings("unchecked")
    private <C2 extends C> List<C2> cache(String stub, Supplier<List<? extends C>> ifAbsent) {
        List<C2> js;
        synchronized (templateCacheLock) {
            js = (List<C2>) templateCache.get(stub);
            if(js == null) {
                js = (List<C2>) ifAbsent.get();
                templateCache.put(stub, js);
            }
        }
        return ListUtils.map(js, j -> (C2) new RandomizeIdVisitor<>().visit(j, 0));
    }
}
