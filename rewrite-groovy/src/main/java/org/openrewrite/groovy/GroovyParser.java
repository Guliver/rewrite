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
package org.openrewrite.groovy;

import groovy.lang.GroovyClassLoader;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.codehaus.groovy.ast.ModuleNode;
import org.codehaus.groovy.control.*;
import org.codehaus.groovy.control.io.InputStreamReaderSource;
import org.intellij.lang.annotations.Language;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.style.NamedStyles;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.stream.Collectors.toList;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class GroovyParser implements JavaParser {
    private static final List<Path> RUNTIME_CLASSPATH = Collections.unmodifiableList(Arrays.stream(System.getProperty("java.class.path").split("\\Q" + System.getProperty("path.separator") + "\\E"))
            .map(cpEntry -> new File(cpEntry).toPath())
            .collect(toList()));

    /**
     * When true, enables a parser to use class types from the in-memory type cache rather than performing
     * a deep equality check. Useful when deep class types have already been built from a separate parsing phase
     * and we want to parse some code snippet without requiring the classpath to be fully specified, using type
     * information we've already learned about in a prior phase.
     */
    private final boolean relaxedClassTypeMatching;

    /**
     * Convenience utility for constructing a parser with binary dependencies on the runtime classpath of the process
     * constructing the parser.
     *
     * @param artifactNames The "artifact name" of the dependency to look for. Artifact name is the artifact portion of
     *                      group:artifact:version coordinates. For example, for Google's Guava (com.google.guava:guava:VERSION),
     *                      the artifact name is "guava".
     * @return A set of paths of jars on the runtime classpath matching the provided artifact names, to the extent such
     * matching jars can be found.
     */
    static List<Path> dependenciesFromClasspath(String... artifactNames) {
        List<Pattern> artifactNamePatterns = Arrays.stream(artifactNames)
                .map(name -> Pattern.compile(name + "-.*?\\.jar$"))
                .collect(toList());

        return RUNTIME_CLASSPATH.stream()
                .filter(cpEntry -> artifactNamePatterns.stream().anyMatch(namePattern -> namePattern.matcher(cpEntry.toString()).find()))
                .collect(toList());
    }

    @Override
    public List<JavaSourceFile> parse(@Language("groovy") String... sources) {
        Pattern packagePattern = Pattern.compile("^package\\s+([^;]+);");
        Pattern classPattern = Pattern.compile("(class|interface|enum)\\s*(<[^>]*>)?\\s+(\\w+)");

        Function<String, String> simpleName = sourceStr -> {
            Matcher classMatcher = classPattern.matcher(sourceStr);
            return classMatcher.find() ? classMatcher.group(3) : null;
        };

        return parseInputs(
                Arrays.stream(sources)
                        .map(sourceFile -> {
                            Matcher packageMatcher = packagePattern.matcher(sourceFile);
                            String pkg = packageMatcher.find() ? packageMatcher.group(1).replace('.', '/') + "/" : "";

                            String className = Optional.ofNullable(simpleName.apply(sourceFile))
                                    .orElse(Long.toString(System.nanoTime())) + ".java";

                            Path path = Paths.get(pkg + className);
                            return new Input(
                                    path,
                                    () -> new ByteArrayInputStream(sourceFile.getBytes())
                            );
                        })
                        .collect(toList()),
                null,
                new InMemoryExecutionContext()
        );
    }

    @Override
    public List<JavaSourceFile> parseInputs(Iterable<Input> sources, @Nullable Path relativeTo, ExecutionContext ctx) {
        List<JavaSourceFile> cus = new ArrayList<>();
        Map<String, JavaType.Class> sharedClassTypes = new HashMap<>();

        for (Input input : sources) {
            CompilerConfiguration configuration = new CompilerConfiguration();
            configuration.setTolerance(Integer.MAX_VALUE);

            SourceUnit unit = new SourceUnit(
                    "doesntmatter",
                    new InputStreamReaderSource(input.getSource(), configuration),
                    configuration,
                    null,
                    new ErrorCollector(configuration)
            );

            GroovyClassLoader transformLoader = new GroovyClassLoader(getClass().getClassLoader());
            CompilationUnit compUnit = new CompilationUnit(null, null, null, transformLoader);
            compUnit.addSource(unit);

            try {
//                removeGrabTransformation(compUnit) <-- codenarc does this for some reason

                compUnit.compile(Phases.CONVERSION);
                ModuleNode ast = unit.getAST();

                GroovyParserVisitor mappingVisitor = new GroovyParserVisitor(
                        input.getPath(),
                        StringUtils.readFully(input.getSource()),
                        relaxedClassTypeMatching,
                        sharedClassTypes,
                        ctx
                );

                cus.add(mappingVisitor.visit(unit, ast));
            } catch (Throwable t) {
                ctx.getOnError().accept(t);
            }
        }

        return cus;
    }

    @Override
    public boolean accept(Path path) {
        return path.toString().endsWith(".groovy");
    }

    @Override
    public GroovyParser reset() {
        return this;
    }

    @Override
    public void setClasspath(Collection<Path> classpath) {

    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        @Nullable
        protected Collection<Path> classpath = RUNTIME_CLASSPATH;

        protected Collection<byte[]> classBytesClasspath = Collections.emptyList();

        @Nullable
        protected Collection<Input> dependsOn;

        protected boolean relaxedClassTypeMatching = false;
        protected boolean logCompilationWarningsAndErrors = false;
        protected final List<NamedStyles> styles = new ArrayList<>();

        public Builder relaxedClassTypeMatching(boolean relaxedClassTypeMatching) {
            this.relaxedClassTypeMatching = relaxedClassTypeMatching;
            return this;
        }

        public Builder logCompilationWarningsAndErrors(boolean logCompilationWarningsAndErrors) {
            this.logCompilationWarningsAndErrors = logCompilationWarningsAndErrors;
            return this;
        }

        public Builder dependsOn(Collection<Input> inputs) {
            this.dependsOn = inputs;
            return this;
        }

        public Builder classpath(Collection<Path> classpath) {
            this.classpath = classpath;
            return this;
        }

        public Builder classpath(String... classpath) {
            this.classpath = dependenciesFromClasspath(classpath);
            return this;
        }

        public Builder classpath(byte[]... classpath) {
            this.classBytesClasspath = Arrays.asList(classpath);
            return this;
        }

        public Builder styles(Iterable<? extends NamedStyles> styles) {
            for (NamedStyles style : styles) {
                this.styles.add(style);
            }
            return this;
        }

        public GroovyParser build() {
            return new GroovyParser(relaxedClassTypeMatching);
        }
    }
}
