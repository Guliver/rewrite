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
package org.openrewrite.java;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.family.c.tree.TextComment;
import org.openrewrite.internal.PropertyPlaceholderHelper;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.Markers;

import java.util.Calendar;
import java.util.Collections;

@Value
@EqualsAndHashCode(callSuper = true)
public class AddLicenseHeader extends Recipe {
    @Option(displayName = "License text",
            description = "The license header text without the block comment. May contain ${CURRENT_YEAR} property.",
            example = "Copyright ${CURRENT_YEAR} the original author or authors...")
    String licenseText;

    @Override
    public String getDisplayName() {
        return "Add license header";
    }

    @Override
    public String getDescription() {
        return "Adds license headers to Java source files when missing. Does not override existing license headers.";
    }

    @Override
    protected JavaVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                if (cu.getComments().isEmpty()) {
                    PropertyPlaceholderHelper propertyPlaceholderHelper = new PropertyPlaceholderHelper("${", "}", null);
                    String formattedLicenseText = "\n * " + propertyPlaceholderHelper.replacePlaceholders(licenseText,
                            k -> {
                                if (k.equals("CURRENT_YEAR")) {
                                    return Integer.toString(Calendar.getInstance().get(Calendar.YEAR));
                                }
                                return System.getProperty(k);
                            }).replace("\n", "\n * ") + "\n ";

                    cu = cu.withComments(Collections.singletonList(
                            new TextComment(true, formattedLicenseText, "\n", Markers.EMPTY)
                    ));
                }
                return cu;
            }

            @Override
            public J.Import visitImport(J.Import impoort, ExecutionContext executionContext) {
                // short circuit everything else
                return impoort;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext executionContext) {
                // short circuit everything else
                return classDecl;
            }
        };
    }
}
