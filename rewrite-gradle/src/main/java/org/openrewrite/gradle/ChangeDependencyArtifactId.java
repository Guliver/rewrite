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
package org.openrewrite.gradle;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.gradle.util.ChangeStringLiteral;
import org.openrewrite.groovy.GroovyVisitor;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.semver.DependencyMatcher;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Value
public class ChangeDependencyArtifactId extends Recipe {
    @Option(displayName = "Dependency pattern",
            description = "A dependency pattern specifying which dependencies should have their artifactId updated. " +
                    DependencyMatcher.STANDARD_OPTION_DESCRIPTION,
            example = "com.fasterxml.jackson*:jackson-module*"
    )
    String dependencyPattern;

    @Option(displayName = "New artifactId",
            description = "The new artifactId to use.",
            example = "rewrite-core")
    String newArtifactId;

    @Option(displayName = "Dependency configuration",
            description = "The dependency configuration to search for dependencies in.",
            example = "api",
            required = false)
    @Nullable
    String configuration;

    @Override
    public String getDisplayName() {
        return "Change Gradle dependency artifactId";
    }

    @Override
    public String getDescription() {
        return "Change the artifactId of a specified Gradle dependency. ";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new IsBuildGradle<>();
    }

    @Override
    public Validated validate() {
        Validated validated = super.validate();
        if (dependencyPattern != null) {
            validated = validated.and(DependencyMatcher.build(dependencyPattern));
        }
        return validated;
    }

    @Override
    protected GroovyVisitor<ExecutionContext> getVisitor() {
        return new GroovyVisitor<ExecutionContext>() {
            final DependencyMatcher depMatcher = DependencyMatcher.build(dependencyPattern).getValue();
            final MethodMatcher dependencyDsl = new MethodMatcher("DependencyHandlerSpec *(..)");

            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext context) {
                J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(method, context);
                if (!dependencyDsl.matches(m) || !(configuration == null || m.getSimpleName().equals(configuration))) {
                    return m;
                }

                List<Expression> depArgs = m.getArguments();
                if (depArgs.get(0) instanceof J.Literal) {
                    String gav = (String) ((J.Literal) depArgs.get(0)).getValue();
                    if (gav != null) {
                        String[] gavs = gav.split(":");
                        if (gavs.length >= 3 && !newArtifactId.equals(gavs[1]) && depMatcher.matches(gavs[0], gavs[1], gavs[2])) {
                            String newGav = gavs[0] + ":" + newArtifactId + ":" + gavs[2];
                            m = m.withArguments(ListUtils.map(m.getArguments(), (n, arg) ->
                                    n == 0 ?
                                            ChangeStringLiteral.withStringValue((J.Literal) arg, newGav) :
                                            arg
                            ));
                        }
                    }
                } else if (depArgs.get(0) instanceof G.MapEntry) {
                    G.MapEntry artifactEntry = null;
                    String groupId = null;
                    String artifactId = null;
                    String version = null;

                    String versionStringDelimiter = "'";
                    for (Expression e : depArgs) {
                        if (!(e instanceof G.MapEntry)) {
                            continue;
                        }
                        G.MapEntry arg = (G.MapEntry) e;
                        if (!(arg.getKey() instanceof J.Literal) || !(arg.getValue() instanceof J.Literal)) {
                            continue;
                        }
                        J.Literal key = (J.Literal) arg.getKey();
                        J.Literal value = (J.Literal) arg.getValue();
                        if (!(key.getValue() instanceof String) || !(value.getValue() instanceof String)) {
                            continue;
                        }
                        String keyValue = (String) key.getValue();
                        String valueValue = (String) value.getValue();
                        if ("group".equals(keyValue)) {
                            groupId = valueValue;
                        } else if ("name".equals(keyValue) && !newArtifactId.equals(valueValue)) {
                            if (value.getValueSource() != null) {
                                versionStringDelimiter = value.getValueSource().substring(0, value.getValueSource().indexOf(valueValue));
                            }
                            artifactEntry = arg;
                            artifactId = valueValue;
                        } else if ("version".equals(keyValue)) {
                            version = valueValue;
                        }
                    }
                    if (groupId == null || artifactId == null
                            || (version == null && !depMatcher.matches(groupId, artifactId))
                            || (version != null && !depMatcher.matches(groupId, artifactId, version))) {
                        return m;
                    }
                    String delimiter = versionStringDelimiter;
                    G.MapEntry finalArtifact = artifactEntry;
                    m = m.withArguments(ListUtils.map(m.getArguments(), arg -> {
                        if (arg == finalArtifact) {
                            return finalArtifact.withValue(((J.Literal) finalArtifact.getValue())
                                    .withValue(newArtifactId)
                                    .withValueSource(delimiter + newArtifactId + delimiter));
                        }
                        return arg;
                    }));
                }

                return m;
            }
        };
    }
}
