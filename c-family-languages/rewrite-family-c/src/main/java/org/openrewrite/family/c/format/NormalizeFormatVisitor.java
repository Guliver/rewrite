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
package org.openrewrite.family.c.format;

import org.openrewrite.family.c.CIsoVisitor;
import org.openrewrite.family.c.tree.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.StringUtils;

import java.util.List;

/**
 * Ensures that whitespace is on the outermost AST element possible.
 */
public class NormalizeFormatVisitor<P> extends CIsoVisitor<P> {
    @Override
    public C.ClassDeclaration visitClassDeclaration(C.ClassDeclaration classDecl, P p) {
        C.ClassDeclaration c = super.visitClassDeclaration(classDecl, p);

        if (!c.getLeadingAnnotations().isEmpty()) {
            c = concatenatePrefix(c, Space.firstPrefix(c.getLeadingAnnotations()));
            c = c.withLeadingAnnotations(Space.formatFirstPrefix(c.getLeadingAnnotations(), Space.EMPTY));
            return c;
        }

        if (!c.getModifiers().isEmpty()) {
            c = concatenatePrefix(c, Space.firstPrefix(c.getModifiers()));
            c = c.withModifiers(Space.formatFirstPrefix(c.getModifiers(), Space.EMPTY));
            return c;
        }

        if (!c.getAnnotations().getKind().getPrefix().isEmpty()) {
            c = concatenatePrefix(c, c.getAnnotations().getKind().getPrefix());
            c = c.getAnnotations().withKind(c.getAnnotations().getKind().withPrefix(Space.EMPTY));
            return c;
        }

        CContainer<C.TypeParameter> typeParameters = c.getPadding().getTypeParameters();
        if (typeParameters != null && !typeParameters.getElements().isEmpty()) {
            c = concatenatePrefix(c, typeParameters.getBefore());
            c = c.getPadding().withTypeParameters(typeParameters.withBefore(Space.EMPTY));
            return c;
        }

        return c.withName(c.getName().withPrefix(c.getName().getPrefix().withWhitespace(" ")));
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public C.MethodDeclaration visitMethodDeclaration(C.MethodDeclaration method, P p) {
        C.MethodDeclaration m = super.visitMethodDeclaration(method, p);

        if (!m.getLeadingAnnotations().isEmpty()) {
            m = concatenatePrefix(m, Space.firstPrefix(m.getLeadingAnnotations()));
            m = m.withLeadingAnnotations(Space.formatFirstPrefix(m.getLeadingAnnotations(), Space.EMPTY));
            return m;
        }

        if (!m.getModifiers().isEmpty()) {
            m = concatenatePrefix(m, Space.firstPrefix(m.getModifiers()));
            m = m.withModifiers(Space.formatFirstPrefix(m.getModifiers(), Space.EMPTY));
            return m;
        }

        if (m.getAnnotations().getTypeParameters() != null) {
            if (!m.getAnnotations().getTypeParameters().getTypeParameters().isEmpty()) {
                m = concatenatePrefix(m, m.getAnnotations().getTypeParameters().getPrefix());
                m = m.getAnnotations().withTypeParameters(m.getAnnotations().getTypeParameters().withPrefix(Space.EMPTY));
            }
            return m;
        }

        if (m.getReturnTypeExpression() != null) {
            if (!m.getReturnTypeExpression().getPrefix().getWhitespace().isEmpty()) {
                m = concatenatePrefix(m, m.getReturnTypeExpression().getPrefix());
                m = m.withReturnTypeExpression(m.getReturnTypeExpression().withPrefix(Space.EMPTY));
            }
            return m;
        }

        m = concatenatePrefix(m, m.getName().getPrefix());
        m = m.withName(m.getName().withPrefix(Space.EMPTY));
        return m;
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public C.VariableDeclarations visitVariableDeclarations(C.VariableDeclarations multiVariable, P p) {
        C.VariableDeclarations v = super.visitVariableDeclarations(multiVariable, p);

        if (!v.getLeadingAnnotations().isEmpty()) {
            v = concatenatePrefix(v, Space.firstPrefix(v.getLeadingAnnotations()));
            v = v.withLeadingAnnotations(Space.formatFirstPrefix(v.getLeadingAnnotations(), Space.EMPTY));
            return v;
        }

        if (!v.getModifiers().isEmpty()) {
            v = concatenatePrefix(v, Space.firstPrefix(v.getModifiers()));
            v = v.withModifiers(Space.formatFirstPrefix(v.getModifiers(), Space.EMPTY));
            return v;
        }

        if (v.getTypeExpression() != null) {
            v = concatenatePrefix(v, v.getTypeExpression().getPrefix());
            v = v.withTypeExpression(v.getTypeExpression().withPrefix(Space.EMPTY));
            return v;
        }

        return v;
    }

    private <C2 extends C> C2 concatenatePrefix(C2 j, Space prefix) {
        String shift = StringUtils.commonMargin(null, j.getPrefix().getWhitespace());

        List<Comment> comments = ListUtils.concatAll(
                j.getComments(),
                ListUtils.map(prefix.getComments(), comment -> {
                    Comment c = comment;
                    if (shift.isEmpty()) {
                        return c;
                    }

                    if (comment instanceof TextComment) {
                        TextComment textComment = (TextComment) c;
                        c = textComment.withText(textComment.getText().replace("\n", "\n" + shift));
                    } else if (c instanceof Javadoc) {
                        c = (Comment) new JavadocVisitor<Integer>() {
                            @Override
                            public Javadoc visitLineBreak(Javadoc.LineBreak lineBreak, Integer integer) {
                                return lineBreak.withMargin(shift + lineBreak.getMargin());
                            }
                        }.visitNonNull((Javadoc) c, 0);
                    }

                    if(c.getSuffix().contains("\n")) {
                        c = c.withSuffix(c.getSuffix().replace("\n", "\n" + shift));
                    }

                    return c;
                })
        );

        return j.withPrefix(j.getPrefix()
                .withWhitespace(j.getPrefix().getWhitespace() + prefix.getWhitespace())
                .withComments(comments));
    }
}
