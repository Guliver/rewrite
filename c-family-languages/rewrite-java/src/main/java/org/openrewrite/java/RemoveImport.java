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

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.EqualsAndHashCode;
import org.openrewrite.family.c.tree.CType;
import org.openrewrite.family.c.tree.Flag;
import org.openrewrite.family.c.tree.Space;
import org.openrewrite.family.c.tree.TypeUtils;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.internal.FormatFirstClassPrefix;
import org.openrewrite.java.style.ImportLayoutStyle;
import org.openrewrite.family.c.style.IntelliJ;
import org.openrewrite.java.tree.J;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.openrewrite.Tree.randomId;

@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
public class RemoveImport<P> extends JavaIsoVisitor<P> {
    @EqualsAndHashCode.Include
    private final String type;

    private final String owner;

    @EqualsAndHashCode.Include
    private final boolean force;

    public RemoveImport(String type) {
        this(type, false);
    }

    @JsonCreator
    public RemoveImport(String type, boolean force) {
        this.type = type;
        this.owner = type.substring(0, Math.max(0, type.lastIndexOf('.')));
        this.force = force;
    }

    @Override
    public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, P p) {

        ImportLayoutStyle importLayoutStyle = Optional.ofNullable(cu.getStyle(ImportLayoutStyle.class))
                .orElse(IntelliJ.importLayout());

        boolean typeUsed = false;
        Set<String> otherTypesInPackageUsed = new TreeSet<>();

        Set<String> methodsAndFieldsUsed = new HashSet<>();
        Set<String> otherMethodsAndFieldsInTypeUsed = new TreeSet<>();
        Set<String> originalImports = new HashSet<>();
        for (J.Import cuImport : cu.getImports()) {
            if (cuImport.getQualid().getType() != null) {
                originalImports.add(((CType.Class) cuImport.getQualid().getType()).getFullyQualifiedName());
            }
        }

        for (CType javaType : cu.getTypesInUse()) {
            if (javaType instanceof CType.Variable) {
                CType.Variable variable = (CType.Variable) javaType;
                CType.FullyQualified fq = TypeUtils.asFullyQualified(variable.getType());
                if (fq != null && (fq.getFullyQualifiedName().equals(type) || fq.getFullyQualifiedName().equals(owner))) {
                    methodsAndFieldsUsed.add(variable.getName());
                }
            } else if (javaType instanceof CType.Method) {
                CType.Method method = (CType.Method) javaType;
                if (method.hasFlags(Flag.Static)) {
                    String declaringType = method.getDeclaringType().getFullyQualifiedName();
                    if (declaringType.equals(type)) {
                        methodsAndFieldsUsed.add(method.getName());
                    } else if (declaringType.equals(owner)) {
                        if (method.getName().equals(type.substring(type.lastIndexOf('.') + 1))) {
                            methodsAndFieldsUsed.add(method.getName());
                        } else {
                            otherMethodsAndFieldsInTypeUsed.add(method.getName());
                        }
                    }
                }
            } else if (javaType instanceof CType.FullyQualified) {
                CType.FullyQualified fullyQualified = (CType.FullyQualified) javaType;
                if (fullyQualified.getFullyQualifiedName().equals(type)) {
                    typeUsed = true;
                } else if (fullyQualified.getFullyQualifiedName().equals(owner) || fullyQualified.getPackageName().equals(owner)) {
                    if (!originalImports.contains(fullyQualified.getFullyQualifiedName())) {
                        otherTypesInPackageUsed.add(fullyQualified.getClassName());
                    }
                }
            }
        }

        J.CompilationUnit c = cu;

        boolean keepImport = typeUsed && !force;
        AtomicReference<Space> spaceForNextImport = new AtomicReference<>();
        c = c.withImports(ListUtils.flatMap(c.getImports(), impoort -> {
            if (spaceForNextImport.get() != null) {
                impoort = impoort.withPrefix(spaceForNextImport.get());
                spaceForNextImport.set(null);
            }

            String typeName = impoort.getTypeName();
            if (impoort.isStatic()) {
                String imported = impoort.getQualid().getSimpleName();
                if ((typeName + "." + imported).equals(type) && (force || !methodsAndFieldsUsed.contains(imported))) {
                    // e.g. remove java.util.Collections.emptySet when type is java.util.Collections.emptySet
                    spaceForNextImport.set(impoort.getPrefix());
                    return null;
                } else if ("*".equals(imported) && (typeName.equals(type) || (typeName + type.substring(type.lastIndexOf('.'))).equals(type))) {
                    if (methodsAndFieldsUsed.isEmpty() && otherMethodsAndFieldsInTypeUsed.isEmpty()) {
                        spaceForNextImport.set(impoort.getPrefix());
                        return null;
                    } else if (methodsAndFieldsUsed.size() + otherMethodsAndFieldsInTypeUsed.size() < importLayoutStyle.getNameCountToUseStarImport()){
                        methodsAndFieldsUsed.addAll(otherMethodsAndFieldsInTypeUsed);
                        return unfoldStarImport(impoort, methodsAndFieldsUsed);
                    }
                } else if (typeName.equals(type) && !methodsAndFieldsUsed.contains(imported)) {
                    // e.g. remove java.util.Collections.emptySet when type is java.util.Collections
                    spaceForNextImport.set(impoort.getPrefix());
                    return null;
                }
            } else if (!keepImport && typeName.equals(type)) {
                if (impoort.getPrefix().isEmpty() || impoort.getPrefix().getLastWhitespace().chars().filter(s -> s == '\n').count() > 1) {
                    spaceForNextImport.set(impoort.getPrefix());
                }
                return null;
            } else if (!keepImport && impoort.getPackageName().equals(owner) &&
                    "*".equals(impoort.getClassName()) &&
                    otherTypesInPackageUsed.size() < importLayoutStyle.getClassCountToUseStarImport()) {
                if (otherTypesInPackageUsed.isEmpty()) {
                    spaceForNextImport.set(impoort.getPrefix());
                    return null;
                } else {
                    return unfoldStarImport(impoort, otherTypesInPackageUsed);
                }
            }
            return impoort;
        }));

        if (c != cu && c.getPackageDeclaration() == null && c.getImports().isEmpty() &&
                c.getPrefix() == Space.EMPTY) {
            doAfterVisit(new FormatFirstClassPrefix<>());
        }

        return c;
    }

    private Object unfoldStarImport(J.Import starImport, Set<String> otherImportsUsed) {
        List<J.Import> unfoldedImports = new ArrayList<>(otherImportsUsed.size());
        int i = 0;
        for (String other : otherImportsUsed) {
            J.Import unfolded = starImport.withQualid(starImport.getQualid().withName(starImport
                    .getQualid().getName().withName(other))).withId(randomId());
            unfoldedImports.add(i++ == 0 ? unfolded : unfolded.withPrefix(Space.format("\n")));
        }
        return unfoldedImports;
    }
}
