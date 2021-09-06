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
package org.openrewrite.java;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.style.ImportLayoutStyle;
import org.openrewrite.family.c.style.IntelliJ;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.*;

/**
 * This recipe will remove any imports for types that are not referenced within the compilation unit. This recipe
 * is aware of the import layout style and will correctly handle unfolding of wildcard imports if the import counts
 * drop below the configured values.
 */
public class RemoveUnusedImports extends Recipe {
    @Override
    public String getDisplayName() {
        return "Remove unused imports";
    }

    @Override
    public String getDescription() {
        return "Remove imports for types that are not referenced.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new RemoveUnusedImportsVisitor();
    }

    private static class RemoveUnusedImportsVisitor extends JavaIsoVisitor<ExecutionContext> {

        @Override
        public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
            ImportLayoutStyle layoutStyle = Optional.ofNullable(cu.getStyle(ImportLayoutStyle.class))
                    .orElse(IntelliJ.importLayout());

            Map<String, Set<String>> methodsAndFieldsByTypeName = new HashMap<>();
            Map<String, Set<CType.FullyQualified>> typesByPackage = new HashMap<>();

            for (CType javaType : cu.getTypesInUse()) {
                if (javaType instanceof CType.Variable) {
                    CType.Variable variable = (CType.Variable) javaType;
                    CType.FullyQualified fq = TypeUtils.asFullyQualified(variable.getType());
                    if (fq != null) {
                        methodsAndFieldsByTypeName.computeIfAbsent(fq.getFullyQualifiedName(), f -> new HashSet<>())
                                .add(variable.getName());
                    }
                } else if (javaType instanceof CType.Method) {
                    CType.Method method = (CType.Method) javaType;
                    if (method.hasFlags(Flag.Static)) {
                        methodsAndFieldsByTypeName.computeIfAbsent(method.getDeclaringType().getFullyQualifiedName(), t -> new HashSet<>())
                                .add(method.getName());
                    }
                } else if (javaType instanceof CType.FullyQualified) {
                    CType.FullyQualified fullyQualified = (CType.FullyQualified) javaType;
                    String packageName = fullyQualified.getClassName().contains(".") ?
                            fullyQualified.getPackageName() + "." + fullyQualified.getClassName().substring(0, fullyQualified.getClassName().lastIndexOf('.')) :
                            fullyQualified.getPackageName();
                    typesByPackage.computeIfAbsent(packageName, f -> new HashSet<>())
                            .add(fullyQualified);
                }
            }

            boolean changed = false;

            // the key is a list because one star import may get replaced with multiple unfolded imports
            List<ImportUsage> importUsage = new ArrayList<>(cu.getPadding().getImports().size());
            for (JRightPadded<J.Import> anImport : cu.getPadding().getImports()) {
                // assume initially that all imports are unused
                ImportUsage singleUsage = new ImportUsage();
                singleUsage.imports.add(anImport);
                importUsage.add(singleUsage);
            }

            // whenever an import statement is found to be used it should be marked true
            for (ImportUsage anImport : importUsage) {
                J.Import elem = anImport.imports.get(0).getElement();
                J.FieldAccess qualid = elem.getQualid();
                J.Identifier name = qualid.getName();

                if (elem.isStatic()) {
                    Set<String> methodsAndFields = methodsAndFieldsByTypeName.get(elem.getTypeName());
                    Set<CType.FullyQualified> staticClasses = typesByPackage.get(elem.getTypeName());
                    if (methodsAndFields == null && staticClasses == null) {
                        anImport.used = false;
                        changed = true;
                    } else if ("*".equals(qualid.getSimpleName())) {
                        if (((methodsAndFields == null ? 0 : methodsAndFields.size()) +
                                (staticClasses == null ? 0 : staticClasses.size())) < layoutStyle.getNameCountToUseStarImport()) {
                            // replacing the star with a series of unfolded imports
                            anImport.imports.clear();

                            // add each unfolded import
                            if (methodsAndFields != null) {
                                methodsAndFields.stream().sorted().forEach(method ->
                                        anImport.imports.add(new JRightPadded<>(elem
                                                .withQualid(qualid.withName(name.withName(method)))
                                                .withPrefix(Space.format("\n")), Space.EMPTY, Markers.EMPTY))
                                );
                            }

                            if (staticClasses != null) {
                                staticClasses.forEach(fqn ->
                                        anImport.imports.add(new JRightPadded<>(elem
                                                .withQualid(qualid.withName(name.withName(fqn.getClassName().contains(".") ? fqn.getClassName().substring(fqn.getClassName().lastIndexOf(".") + 1) : fqn.getClassName())))
                                                .withPrefix(Space.format("\n")), Space.EMPTY, Markers.EMPTY))
                                );
                            }

                            // move whatever the original prefix of the star import was to the first unfolded import
                            anImport.imports.set(0, anImport.imports.get(0).withElement(anImport.imports.get(0)
                                    .getElement().withPrefix(elem.getPrefix())));

                            changed = true;
                        }
                    } else if (staticClasses != null && staticClasses.stream().anyMatch(c -> elem.getQualid().printTrimmed().equals(c.getFullyQualifiedName())) ||
                            methodsAndFields != null && methodsAndFields.contains(qualid.getSimpleName())) {
                        anImport.used = true;
                    } else {
                        anImport.used = false;
                        changed = true;
                    }
                } else {
                    Set<CType.FullyQualified> types = typesByPackage.get(elem.getPackageName());
                    if (types == null) {
                        anImport.used = false;
                        changed = true;
                    } else if ("*".equals(elem.getQualid().getSimpleName())) {
                        if (types.size() < layoutStyle.getClassCountToUseStarImport()) {
                            // replacing the star with a series of unfolded imports
                            anImport.imports.clear();

                            // add each unfolded import
                            types.stream().map(CType.FullyQualified::getClassName).sorted().distinct().forEach(type ->
                                    anImport.imports.add(new JRightPadded<>(elem
                                            .withQualid(qualid.withName(name.withName(type)))
                                            .withPrefix(Space.format("\n")), Space.EMPTY, Markers.EMPTY))
                            );

                            // move whatever the original prefix of the star import was to the first unfolded import
                            anImport.imports.set(0, anImport.imports.get(0).withElement(anImport.imports.get(0)
                                    .getElement().withPrefix(elem.getPrefix())));

                            changed = true;
                        }
                    } else if (types.stream().noneMatch(c -> elem.isFromType(c.getFullyQualifiedName()))) {
                        anImport.used = false;
                        changed = true;
                    }
                }
            }

            if (changed) {
                List<JRightPadded<J.Import>> imports = new ArrayList<>();
                Space lastUnusedImportSpace = null;
                for (ImportUsage anImportGroup : importUsage) {
                    if (anImportGroup.used) {
                        List<JRightPadded<J.Import>> importGroup = anImportGroup.imports;
                        for (int i = 0; i < importGroup.size(); i++) {
                            JRightPadded<J.Import> anImport = importGroup.get(i);
                            if (i == 0 && lastUnusedImportSpace != null && anImport.getElement().getPrefix().getLastWhitespace()
                                    .chars().filter(c -> c == '\n').count() <= 1) {
                                anImport = anImport.withElement(anImport.getElement().withPrefix(lastUnusedImportSpace));
                            }
                            imports.add(anImport);
                        }
                        lastUnusedImportSpace = null;
                    } else if(lastUnusedImportSpace == null) {
                        lastUnusedImportSpace = anImportGroup.imports.get(0).getElement().getPrefix();
                    }
                }

                cu = cu.getPadding().withImports(imports);
                if (cu.getImports().isEmpty() && !cu.getClasses().isEmpty()) {
                    cu = autoFormat(cu, cu.getClasses().get(0).getName(), ctx, getCursor());
                }
            }

            return cu;
        }
    }

    private static class ImportUsage {
        final List<JRightPadded<J.Import>> imports = new ArrayList<>();
        boolean used = true;
    }
}
