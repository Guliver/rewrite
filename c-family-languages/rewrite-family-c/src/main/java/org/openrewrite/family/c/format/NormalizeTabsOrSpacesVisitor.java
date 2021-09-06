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
package org.openrewrite.family.c.format;

import org.jetbrains.annotations.NotNull;
import org.openrewrite.Tree;
import org.openrewrite.family.c.CIsoVisitor;
import org.openrewrite.family.c.style.TabsAndIndentsStyle;
import org.openrewrite.family.c.tree.Comment;
import org.openrewrite.family.c.tree.Space;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.internal.lang.Nullable;

public class NormalizeTabsOrSpacesVisitor<P> extends CIsoVisitor<P> {
    @Nullable
    private final Tree stopAfter;

    private final TabsAndIndentsStyle style;

    public NormalizeTabsOrSpacesVisitor(TabsAndIndentsStyle style) {
        this(style, null);
    }

    public NormalizeTabsOrSpacesVisitor(TabsAndIndentsStyle style, @Nullable Tree stopAfter) {
        this.style = style;
        this.stopAfter = stopAfter;
    }

    @Override
    public Space visitSpace(Space space, Space.Location loc, P p) {
        Space s = space.withWhitespace(normalizeAfterFirstNewline(space.getWhitespace()));

        return s.withComments(ListUtils.map(s.getComments(), comment -> {
            Comment c = comment;
            if (c.isMultiline()) {
                if (c instanceof Javadoc) {
                    return (Comment) new JavadocVisitor<Integer>() {
                        @Override
                        public Javadoc visitLineBreak(Javadoc.LineBreak lineBreak, Integer integer) {
                            return lineBreak.withMargin(normalize(lineBreak.getMargin()));
                        }
                    }.visitNonNull((Javadoc) c, 0);
                } else {
                    TextComment textComment = (TextComment) c;
                    if (textComment.getText().contains("\t")) {
                        c = textComment.withText(normalize(textComment.getText()));
                    }
                }
            }

            return c.withSuffix(normalizeAfterFirstNewline(c.getSuffix()));
        }));
    }

    @NotNull
    private String normalizeAfterFirstNewline(String text) {
        int firstNewline = text.indexOf('\n');
        if (firstNewline >= 0 && firstNewline != text.length() - 1) {
            return text.substring(0, firstNewline + 1) + normalize(text.substring(firstNewline + 1));
        }
        return text;
    }

    private String normalize(String text) {
        if (!StringUtils.isNullOrEmpty(text)) {
            if (style.getUseTabCharacter() ? text.contains(" ") : text.contains("\t")) {
                StringBuilder textBuilder = new StringBuilder();
                int consecutiveSpaces = 0;
                boolean inMargin = true;
                char[] charArray = text.toCharArray();
                outer:
                for (int i = 0; i < charArray.length; i++) {
                    char c = charArray[i];
                    if (c == '\n' || c == '\r') {
                        inMargin = true;
                        consecutiveSpaces = 0;
                        textBuilder.append(c);
                    } else if (!inMargin) {
                        textBuilder.append(c);
                    } else if (style.getUseTabCharacter() && c == ' ') {
                        int j = i + 1;
                        for (; j < charArray.length && j < style.getTabSize(); j++) {
                            if (charArray[j] != ' ') {
                                continue outer;
                            }
                        }
                        i = j + 1;
                        textBuilder.append('\t');
                    } else if (!style.getUseTabCharacter() && c == '\t') {
                        for (int j = 0; j < style.getTabSize() - (consecutiveSpaces % style.getTabSize()); j++) {
                            textBuilder.append(' ');
                        }
                        consecutiveSpaces = 0;
                    } else if (Character.isWhitespace(c)) {
                        consecutiveSpaces++;
                        textBuilder.append(c);
                    } else {
                        inMargin = false;
                        textBuilder.append(c);
                    }
                }
                return textBuilder.toString();
            }
        }

        return text;
    }

    @Nullable
    @Override
    public J postVisit(J tree, P p) {
        if (stopAfter != null && stopAfter.isScope(tree)) {
            getCursor().putMessageOnFirstEnclosing(J.CompilationUnit.class, "stop", true);
        }
        return super.postVisit(tree, p);
    }

    @Nullable
    @Override
    public J visit(@Nullable Tree tree, P p) {
        if (getCursor().getNearestMessage("stop") != null) {
            return (J) tree;
        }
        return super.visit(tree, p);
    }
}
