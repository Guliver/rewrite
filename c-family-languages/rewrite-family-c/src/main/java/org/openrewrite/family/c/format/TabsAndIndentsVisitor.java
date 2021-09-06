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

import org.openrewrite.Cursor;
import org.openrewrite.Tree;
import org.openrewrite.family.c.CIsoVisitor;
import org.openrewrite.family.c.tree.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavadocVisitor;
import org.openrewrite.family.c.style.TabsAndIndentsStyle;
import org.openrewrite.java.tree.*;

import java.util.List;
import java.util.Optional;

public class TabsAndIndentsVisitor<P> extends CIsoVisitor<P> {
    @Nullable
    private final Tree stopAfter;

    private final TabsAndIndentsStyle style;

    public TabsAndIndentsVisitor(TabsAndIndentsStyle style) {
        this(style, null);
    }

    public TabsAndIndentsVisitor(TabsAndIndentsStyle style, @Nullable Tree stopAfter) {
        this.style = style;
        this.stopAfter = stopAfter;
    }

    @Override
    @Nullable
    public C visit(@Nullable Tree tree, P p, Cursor parent) {
        setCursor(parent);
        for (Cursor c = parent; c != null; c = c.getParent()) {
            Object v = c.getValue();
            Space space = null;
            if (v instanceof C) {
                space = ((C) v).getPrefix();
            } else if (v instanceof CRightPadded) {
                space = ((CRightPadded<?>) v).getAfter();
            } else if (v instanceof CLeftPadded) {
                space = ((CLeftPadded<?>) v).getBefore();
            } else if (v instanceof CContainer) {
                space = ((CContainer<?>) v).getBefore();
            }

            if (space != null && space.getLastWhitespace().contains("\n")) {
                int indent = findIndent(space);
                if (indent != 0) {
                    c.putMessage("lastIndent", indent);
                }
            }
        }
        preVisit((C) parent.getPath(C.class::isInstance).next(), p);
        return visit(tree, p);
    }

    @Override
    @Nullable
    public C preVisit(C tree, P p) {
        if (tree instanceof J.CompilationUnit ||
                tree instanceof C.Package ||
                tree instanceof J.Import ||
                tree instanceof C.Label ||
                tree instanceof C.DoWhileLoop ||
                tree instanceof C.ArrayDimension ||
                tree instanceof C.ClassDeclaration) {
            getCursor().putMessage("indentType", IndentType.ALIGN);
        } else if (tree instanceof C.Block ||
                tree instanceof C.If ||
                tree instanceof C.If.Else ||
                tree instanceof C.ForLoop ||
                tree instanceof C.ForEachLoop ||
                tree instanceof C.WhileLoop ||
                tree instanceof C.Case ||
                tree instanceof C.EnumValueSet) {
            getCursor().putMessage("indentType", IndentType.INDENT);
        } else {
            getCursor().putMessage("indentType", IndentType.CONTINUATION_INDENT);
        }

        return super.preVisit(tree, p);
    }

    @Override
    public C visitForControl(C.ForLoop.Control control, P p) {
        // FIXME fix formatting of control sections
        return control;
    }

    @Override
    public C visitForEachControl(C.ForEachLoop.Control control, P p) {
        // FIXME fix formatting of control sections
        return control;
    }

    @Override
    public Space visitSpace(Space space, Space.Location loc, P p) {
        boolean alignToAnnotation = false;
        Cursor parent = getCursor().getParent();
        if (parent != null && parent.getValue() instanceof C.Annotation) {
            parent.getParentOrThrow().putMessage("afterAnnotation", true);
        } else if (parent != null && !getCursor().getParentOrThrow().getPath(C.Annotation.class::isInstance).hasNext()) {
            // when annotations are on their own line, other parts of the declaration that follow are aligned left to it
            alignToAnnotation = getCursor().pollNearestMessage("afterAnnotation") != null &&
                    !(getCursor().getParentOrThrow().getValue() instanceof C.Annotation);
        }

        if (space.getComments().isEmpty() && !space.getLastWhitespace().contains("\n") || parent == null) {
            return space;
        }

        int indent = Optional.ofNullable(getCursor().<Integer>getNearestMessage("lastIndent")).orElse(0);

        IndentType indentType = Optional.ofNullable(getCursor().getParentOrThrow().
                <IndentType>getNearestMessage("indentType")).orElse(IndentType.ALIGN);

        // block spaces are always aligned to their parent
        boolean alignBlockPrefixToParent = loc.equals(Space.Location.BLOCK_PREFIX) && space.getWhitespace().contains("\n") &&
                // ignore init blocks.
                (getCursor().getValue() instanceof C.Block && !(getCursor().dropParentUntil(J.class::isInstance).getValue() instanceof C.Block));

        boolean alignBlockToParent = loc.equals(Space.Location.BLOCK_END) ||
                loc.equals(Space.Location.NEW_ARRAY_INITIALIZER_SUFFIX) ||
                loc.equals(Space.Location.CATCH_PREFIX) ||
                loc.equals(Space.Location.TRY_FINALLY) ||
                loc.equals(Space.Location.ELSE_PREFIX);

        if (loc.equals(Space.Location.EXTENDS) && space.getWhitespace().contains("\n")) {
            indentType = IndentType.CONTINUATION_INDENT;
        }

        if (alignBlockPrefixToParent || alignBlockToParent || alignToAnnotation) {
            indentType = IndentType.ALIGN;
        }

        switch (indentType) {
            case ALIGN:
                break;
            case INDENT:
                indent += style.getIndentSize();
                break;
            case CONTINUATION_INDENT:
                indent += style.getContinuationIndent();
                break;
        }

        Space s = indentTo(space, indent, loc);
        if (!(getCursor().getValue() instanceof CLeftPadded) && !(getCursor().getValue() instanceof C.EnumValueSet)) {
            getCursor().putMessage("lastIndent", indent);
        }
        return s;
    }

    @Override
    public <T> CRightPadded<T> visitRightPadded(@Nullable CRightPadded<T> right, CRightPadded.Location loc, P p) {
        if (right == null) {
            //noinspection ConstantConditions
            return null;
        }

        setCursor(new Cursor(getCursor(), right));

        T t = right.getElement();
        Space after;

        int indent = Optional.ofNullable(getCursor().<Integer>getNearestMessage("lastIndent")).orElse(0);
        if (right.getElement() instanceof J) {
            J elem = (J) right.getElement();
            if ((right.getAfter().getLastWhitespace().contains("\n") ||
                    elem.getPrefix().getLastWhitespace().contains("\n"))) {
                switch (loc) {
                    case FOR_CONDITION:
                    case FOR_UPDATE: {
                        C.ForLoop.Control control = getCursor().getParentOrThrow().getValue();
                        Space initPrefix = Space.firstPrefix(control.getInit());
                        if (!initPrefix.getLastWhitespace().contains("\n")) {
                            int initIndent = forInitColumn();
                            getCursor().getParentOrThrow().putMessage("lastIndent", initIndent - style.getContinuationIndent());
                            elem = visitAndCast(elem, p);
                            getCursor().getParentOrThrow().putMessage("lastIndent", indent);
                            after = indentTo(right.getAfter(), initIndent, loc.getAfterLocation());
                        } else {
                            elem = visitAndCast(elem, p);
                            after = visitSpace(right.getAfter(), loc.getAfterLocation(), p);
                        }
                        break;
                    }
                    case METHOD_DECLARATION_PARAMETER: {
                        CContainer<C> container = getCursor().getParentOrThrow().getValue();
                        C firstArg = container.getElements().iterator().next();
                        if (firstArg.getPrefix().getWhitespace().isEmpty()) {
                            after = right.getAfter();
                        } else if (firstArg.getPrefix().getLastWhitespace().contains("\n")) {
                            // if the first argument is on its own line, align all arguments to be continuation indented
                            elem = visitAndCast(elem, p);
                            after = indentTo(right.getAfter(), indent, loc.getAfterLocation());
                        } else {
                            // align to first argument when the first argument isn't on its own line
                            int firstArgIndent = findIndent(firstArg.getPrefix());
                            getCursor().getParentOrThrow().putMessage("lastIndent", firstArgIndent);
                            elem = visitAndCast(elem, p);
                            getCursor().getParentOrThrow().putMessage("lastIndent", indent);
                            after = indentTo(right.getAfter(), firstArgIndent, loc.getAfterLocation());
                        }
                        break;
                    }
                    case METHOD_INVOCATION_ARGUMENT:
                        if (!elem.getPrefix().getLastWhitespace().contains("\n")) {
                            if (elem instanceof C.Lambda) {
                                J body = ((C.Lambda) elem).getBody();
                                if (!(body instanceof C.Binary)) {
                                    if (!body.getPrefix().getLastWhitespace().contains("\n")) {
                                        getCursor().getParentOrThrow().putMessage("lastIndent", indent + style.getContinuationIndent());
                                    }
                                }
                            }
                        }
                        elem = visitAndCast(elem, p);
                        after = indentTo(right.getAfter(), indent, loc.getAfterLocation());
                        break;
                    case NEW_CLASS_ARGUMENTS:
                    case ARRAY_INDEX:
                    case PARENTHESES:
                    case TYPE_PARAMETER: {
                        elem = visitAndCast(elem, p);
                        after = indentTo(right.getAfter(), indent, loc.getAfterLocation());
                        break;
                    }
                    case METHOD_SELECT: {
                        for (Cursor cursor = getCursor(); ; cursor = cursor.getParentOrThrow()) {
                            if (cursor.getValue() instanceof CRightPadded) {
                                cursor = cursor.getParentOrThrow();
                            }
                            if (!(cursor.getValue() instanceof C.MethodInvocation)) {
                                break;
                            }
                            Integer methodIndent = cursor.getNearestMessage("lastIndent");
                            if (methodIndent != null) {
                                indent = methodIndent;
                            }
                        }

                        getCursor().getParentOrThrow().putMessage("lastIndent", indent);
                        elem = visitAndCast(elem, p);
                        after = visitSpace(right.getAfter(), loc.getAfterLocation(), p);
                        getCursor().getParentOrThrow().putMessage("lastIndent", indent + style.getContinuationIndent());
                        break;
                    }
                    case ANNOTATION_ARGUMENT:
                        CContainer<J> args = getCursor().getParentOrThrow().getValue();
                        elem = visitAndCast(elem, p);

                        // the end parentheses on an annotation is aligned to the annotation
                        if (args.getPadding().getElements().get(args.getElements().size() - 1) == right) {
                            getCursor().getParentOrThrow().putMessage("indentType", IndentType.ALIGN);
                        }

                        after = visitSpace(right.getAfter(), loc.getAfterLocation(), p);
                        break;
                    default:
                        elem = visitAndCast(elem, p);
                        after = visitSpace(right.getAfter(), loc.getAfterLocation(), p);
                }
            } else {
                switch (loc) {
                    case NEW_CLASS_ARGUMENTS:
                    case METHOD_INVOCATION_ARGUMENT:
                        if (!elem.getPrefix().getLastWhitespace().contains("\n")) {
                            CContainer<J> args = getCursor().getParentOrThrow().getValue();
                            boolean seenArg = false;
                            boolean anyOtherArgOnOwnLine = false;
                            for (CRightPadded<J> arg : args.getPadding().getElements()) {
                                if (arg == getCursor().getValue()) {
                                    seenArg = true;
                                    continue;
                                }
                                if (seenArg) {
                                    if (arg.getElement().getPrefix().getLastWhitespace().contains("\n")) {
                                        anyOtherArgOnOwnLine = true;
                                        break;
                                    }
                                }
                            }
                            if (!anyOtherArgOnOwnLine) {
                                elem = visitAndCast(elem, p);
                                after = indentTo(right.getAfter(), indent, loc.getAfterLocation());
                                break;
                            }
                        }
                        if (!(elem instanceof C.Binary)) {
                            if (!(elem instanceof C.MethodInvocation)) {
                                getCursor().putMessage("lastIndent", indent + style.getContinuationIndent());
                            } else if (elem.getPrefix().getLastWhitespace().contains("\n")) {
                                getCursor().putMessage("lastIndent", indent + style.getContinuationIndent());
                            } else {
                                C.MethodInvocation methodInvocation = (C.MethodInvocation) elem;
                                Expression select = methodInvocation.getSelect();
                                if (select instanceof C.FieldAccess || select instanceof C.Identifier || select instanceof C.MethodInvocation) {
                                    getCursor().putMessage("lastIndent", indent + style.getContinuationIndent());
                                }
                            }
                        }
                        elem = visitAndCast(elem, p);
                        after = visitSpace(right.getAfter(), loc.getAfterLocation(), p);
                        break;
                    default:
                        elem = visitAndCast(elem, p);
                        after = right.getAfter();
                }
            }

            //noinspection unchecked
            t = (T) elem;
        } else {
            after = visitSpace(right.getAfter(), loc.getAfterLocation(), p);
        }

        setCursor(getCursor().getParent());
        return (after == right.getAfter() && t == right.getElement()) ? right : new CRightPadded<>(t, after, right.getMarkers());
    }

    @Override
    public <J2 extends J> CContainer<J2> visitContainer(CContainer<J2> container, CContainer.Location loc, P p) {
        setCursor(new Cursor(getCursor(), container));

        Space before;
        List<CRightPadded<J2>> js;

        int indent = Optional.ofNullable(getCursor().<Integer>getNearestMessage("lastIndent")).orElse(0);
        if (container.getBefore().getLastWhitespace().contains("\n")) {
            switch (loc) {
                case TYPE_PARAMETERS:
                case IMPLEMENTS:
                case THROWS:
                case NEW_CLASS_ARGUMENTS:
                    before = indentTo(container.getBefore(), indent + style.getContinuationIndent(), loc.getBeforeLocation());
                    getCursor().putMessage("indentType", IndentType.ALIGN);
                    getCursor().putMessage("lastIndent", indent + style.getContinuationIndent());
                    js = ListUtils.map(container.getPadding().getElements(), t -> visitRightPadded(t, loc.getElementLocation(), p));
                    break;
                default:
                    before = visitSpace(container.getBefore(), loc.getBeforeLocation(), p);
                    js = ListUtils.map(container.getPadding().getElements(), t -> visitRightPadded(t, loc.getElementLocation(), p));
            }
        } else {
            switch (loc) {
                case IMPLEMENTS:
                case METHOD_INVOCATION_ARGUMENTS:
                case NEW_CLASS_ARGUMENTS:
                case TYPE_PARAMETERS:
                case THROWS:
                    getCursor().putMessage("indentType", IndentType.CONTINUATION_INDENT);
                    before = visitSpace(container.getBefore(), loc.getBeforeLocation(), p);
                    js = ListUtils.map(container.getPadding().getElements(), t -> visitRightPadded(t, loc.getElementLocation(), p));
                    break;
                default:
                    before = visitSpace(container.getBefore(), loc.getBeforeLocation(), p);
                    js = ListUtils.map(container.getPadding().getElements(), t -> visitRightPadded(t, loc.getElementLocation(), p));
            }
        }

        setCursor(getCursor().getParent());
        return js == container.getPadding().getElements() && before == container.getBefore() ?
                container :
                CContainer.build(before, js, container.getMarkers());
    }

    private Space indentTo(Space space, int column, Space.Location spaceLocation) {
        Space s = space;
        String whitespace = s.getWhitespace();

        if (spaceLocation == Space.Location.COMPILATION_UNIT_PREFIX && !StringUtils.isNullOrEmpty(whitespace)) {
            s = s.withWhitespace("");
        } else if (s.getComments().isEmpty() && !s.getLastWhitespace().contains("\n")) {
            return s;
        }

        if (s.getComments().isEmpty()) {
            int indent = findIndent(s);
            if (indent != column) {
                int shift = column - indent;
                s = s.withWhitespace(indent(whitespace, shift));
            }
        } else {
            boolean hasFileLeadingComment = !space.getComments().isEmpty() && (
                    spaceLocation == Space.Location.COMPILATION_UNIT_PREFIX ||
                            (spaceLocation == Space.Location.CLASS_DECLARATION_PREFIX && space.getComments().get(0).isMultiline())
            );

            int finalColumn = spaceLocation == Space.Location.BLOCK_END ?
                    column + style.getIndentSize() : column;
            String lastIndent = space.getWhitespace().substring(space.getWhitespace().lastIndexOf('\n') + 1);
            int indent = getLengthOfWhitespace(StringUtils.indent(lastIndent));

            if (indent != finalColumn) {
                if (whitespace.contains("\n") || hasFileLeadingComment) {
                    int shift = finalColumn - indent;
                    s = s.withWhitespace(whitespace.substring(0, whitespace.lastIndexOf('\n') + 1) +
                            indent(lastIndent, shift));
                }

                Space finalSpace = s;
                s = s.withComments(ListUtils.map(s.getComments(), (i, c) -> {
                    String priorSuffix = i == 0 ?
                            space.getWhitespace() :
                            finalSpace.getComments().get(i - 1).getSuffix();

                    int toColumn = spaceLocation == Space.Location.BLOCK_END && i != finalSpace.getComments().size() - 1 ?
                            column + style.getIndentSize() :
                            column;

                    Comment c2 = c;
                    if (priorSuffix.contains("\n") || hasFileLeadingComment) {
                        c2 = indentComment(c, priorSuffix, toColumn);
                    }

                    if (c2.getSuffix().contains("\n")) {
                        int suffixIndent = getLengthOfWhitespace(c2.getSuffix());
                        int shift = toColumn - suffixIndent;
                        c2 = c2.withSuffix(indent(c2.getSuffix(), shift));
                    }

                    return c2;
                }));
            }
        }

        return s;
    }

    private Comment indentComment(Comment comment, String priorSuffix, int column) {
        if (comment instanceof TextComment) {
            TextComment textComment = (TextComment) comment;
            if (!textComment.getText().contains("\n")) {
                return comment;
            }

            // the margin is the baseline for how much we should shift left or right
            String margin = StringUtils.commonMargin(null, priorSuffix);

            int indent = getLengthOfWhitespace(margin);
            int shift = column - indent;

            if (shift > 0) {
                String newMargin = indent(margin, shift);
                if (textComment.isMultiline()) {
                    StringBuilder multiline = new StringBuilder();
                    char[] chars = textComment.getText().toCharArray();
                    for (int i = 0; i < chars.length; i++) {
                        char c = chars[i];
                        if (c == '\n') {
                            multiline.append(c).append(newMargin);
                            i += margin.length();
                        } else {
                            multiline.append(c);
                        }
                    }
                    return textComment.withText(multiline.toString());
                }
            } else if (shift < 0) {
                if (textComment.isMultiline()) {
                    StringBuilder multiline = new StringBuilder();
                    char[] chars = textComment.getText().toCharArray();
                    for (int i = 0; i < chars.length; i++) {
                        char c = chars[i];
                        if (c == '\n') {
                            multiline.append(c);
                            for (int j = 0; j < Math.abs(shift) && (chars[j + i + 1] == ' ' || chars[j + i + 1] == '\t'); j++) {
                                i++;
                            }
                        } else {
                            multiline.append(c);
                        }
                    }
                    return textComment.withText(multiline.toString());
                }
            } else {
                return textComment;
            }
        } else if (comment instanceof Javadoc.DocComment) {
            String margin = StringUtils.commonMargin(null, priorSuffix);

            int indent = getLengthOfWhitespace(margin);
            int shift = column - indent;
            if (shift != 0) {
                return (Javadoc.DocComment) new JavadocVisitor<Integer>() {
                    @Override
                    public Javadoc visitLineBreak(Javadoc.LineBreak lineBreak, Integer p) {
                        if (shift < 0) {
                            StringBuilder margin = new StringBuilder();
                            char[] charArray = lineBreak.getMargin().toCharArray();
                            boolean inMargin = true;
                            for (int i = 0; i < charArray.length; i++) {
                                char c = charArray[i];
                                if(i < Math.abs(shift) && inMargin) {
                                    if(!Character.isWhitespace(c)) {
                                        inMargin = false;
                                        margin.append(c);
                                    }
                                    continue;
                                }
                                margin.append(c);
                            }
                            return lineBreak.withMargin(margin.toString());
                        } else{
                            return lineBreak.withMargin(indent("", shift) +
                                    lineBreak.getMargin());
                        }
                    }
                }.visitNonNull((Javadoc.DocComment) comment, 0);
            }
        }

        return comment;
    }

    private String indent(String whitespace, int shift) {
        StringBuilder newWhitespace = new StringBuilder(whitespace);
        shift(newWhitespace, shift);
        return newWhitespace.toString();
    }

    private void shift(StringBuilder text, int shift) {
        int tabIndent = style.getTabSize();
        if (!style.getUseTabCharacter()) {
            tabIndent = Integer.MAX_VALUE;
        }

        if (shift > 0) {
            for (int i = 0; i < shift / tabIndent; i++) {
                text.append('\t');
            }

            for (int i = 0; i < shift % tabIndent; i++) {
                text.append(' ');
            }
        } else {
            int len;
            if (style.getUseTabCharacter()) {
                len = text.length() + (shift / tabIndent);
            } else {
                len = text.length() + shift;
            }
            if (len >= 0) {
                text.delete(len, text.length());
            }
        }
    }

    private int findIndent(Space space) {
        String indent = space.getIndent();
        return getLengthOfWhitespace(indent);
    }

    private int getLengthOfWhitespace(@Nullable String whitespace) {
        if (whitespace == null) {
            return 0;
        }

        int size = 0;
        for (char c : whitespace.toCharArray()) {
            size += c == '\t' ? style.getTabSize() : 1;
            if (c == '\n' || c == '\r') {
                size = 0;
            }
        }
        return size;
    }

    private int forInitColumn() {
        Cursor forCursor = getCursor().dropParentUntil(C.ForLoop.class::isInstance);
        C.ForLoop forLoop = forCursor.getValue();
        Object parent = forCursor.getParentOrThrow().getValue();
        @SuppressWarnings("ConstantConditions") J alignTo = parent instanceof C.Label ?
                ((C.Label) parent).withStatement(forLoop.withBody(null)) :
                forLoop.withBody(null);

        int column = 0;
        boolean afterInitStart = false;
        for (char c : alignTo.print().toCharArray()) {
            if (c == '(') {
                afterInitStart = true;
            } else if (afterInitStart && !Character.isWhitespace(c)) {
                return column - 1;
            }
            column++;
        }
        throw new IllegalStateException("For loops must have a control section");
    }

    @Nullable
    @Override
    public C postVisit(J tree, P p) {
        if (stopAfter != null && stopAfter.isScope(tree)) {
            getCursor().putMessageOnFirstEnclosing(J.CompilationUnit.class, "stop", true);
        }
        return super.postVisit(tree, p);
    }

    @Nullable
    @Override
    public C visit(@Nullable Tree tree, P p) {
        if (getCursor().getNearestMessage("stop") != null) {
            return (J) tree;
        }
        return super.visit(tree, p);
    }

    private enum IndentType {
        ALIGN,
        INDENT,
        CONTINUATION_INDENT
    }
}
