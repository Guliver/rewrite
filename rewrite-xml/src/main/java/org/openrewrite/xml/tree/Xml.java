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
package org.openrewrite.xml.tree;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.With;
import lombok.experimental.FieldDefaults;
import org.intellij.lang.annotations.Language;
import org.openrewrite.Cursor;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.marker.Marker;
import org.openrewrite.marker.Markers;
import org.openrewrite.xml.XmlParser;
import org.openrewrite.xml.XmlVisitor;
import org.openrewrite.xml.internal.XmlListMarkersVisitor;

import java.io.Serializable;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.openrewrite.Tree.randomId;

/**
 * The XML <a href="https://www.w3.org/TR/xml11/#syntax">spec</a>.
 */
@JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class, property = "@ref")
public interface Xml extends Serializable, Tree {

    @SuppressWarnings("unchecked")
    @Override
    default <R extends Tree, P> R accept(TreeVisitor<R, P> v, P p) {
        return v instanceof XmlVisitor ?
                (R) acceptXml((XmlVisitor<P>) v, p) : v.defaultValue(this, p);
    }

    @Nullable
    default <P> Xml acceptXml(XmlVisitor<P> v, P p) {
        return v.defaultValue(this, p);
    }

    @Override
    default <P> boolean isAcceptable(TreeVisitor<?, P> v, P p) {
        return v instanceof XmlVisitor;
    }

    String getPrefix();

    Xml withPrefix(String prefix);

    <T extends Xml> T withMarkers(Markers markers);

    Markers getMarkers();

    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    @Data
    @With
    class Document implements Xml, SourceFile {
        @EqualsAndHashCode.Include
        UUID id;

        Path sourcePath;
        String prefix;
        Markers markers;
        Prolog prolog;
        Tag root;
        String eof;

        @Override
        public <P> Xml acceptXml(XmlVisitor<P> v, P p) {
            return v.visitDocument(this, p);
        }
    }

    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    @Data
    @With
    class Prolog implements Xml {
        @EqualsAndHashCode.Include
        UUID id;

        String prefix;
        Markers markers;

        @Nullable
        List<ProcessingInstruction> xmlDecls;

        List<Misc> misc;

        @Override
        public <P> Xml acceptXml(XmlVisitor<P> v, P p) {
            return v.visitProlog(this, p);
        }
    }

    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    @Data
    @With
    class ProcessingInstruction implements Xml, Misc {
        @EqualsAndHashCode.Include
        UUID id;

        String prefix;
        Markers markers;
        String name;
        List<Attribute> attributes;

        /**
         * Space before '&gt;'
         */
        @With
        String beforeTagDelimiterPrefix;

        @Override
        public <P> Xml acceptXml(XmlVisitor<P> v, P p) {
            return v.visitProcessingInstruction(this, p);
        }
    }

    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    @Data
    class Tag implements Xml, Content {
        @EqualsAndHashCode.Include
        @With
        UUID id;

        @With
        String prefix;

        @With
        Markers markers;

        /**
         * XML does not allow space between the '&lt;' and tag name.
         */
        String name;

        public static Xml.Tag build(@Language("xml") String tagSource) {
            return new XmlParser().parse(tagSource).get(0).getRoot();
        }

        public Tag withName(String name) {
            return new Tag(id, prefix, markers, name, attributes, content,
                    closing == null ? null : closing.withName(name),
                    beforeTagDelimiterPrefix);
        }

        public Tag withValue(String value) {
            CharData charData;
            if (content != null && content.get(0) instanceof CharData) {
                charData = ((CharData) content.get(0)).withText(value);
            } else {
                charData = new CharData(randomId(), "", Markers.EMPTY,
                        false, value, "");
            }
            return withContent(Collections.singletonList(charData));
        }

        @With
        List<Attribute> attributes;

        @Nullable
        List<? extends Content> content;

        public Optional<Tag> getChild(String name) {
            return content == null ? Optional.empty() : content.stream()
                    .filter(t -> t instanceof Xml.Tag)
                    .map(Tag.class::cast)
                    .filter(t -> t.getName().equals(name))
                    .findAny();
        }

        public List<Tag> getChildren(String name) {
            return content == null ? emptyList() : content.stream()
                    .filter(t -> t instanceof Xml.Tag)
                    .map(Tag.class::cast)
                    .filter(t -> t.getName().equals(name))
                    .collect(toList());
        }

        public List<Tag> getChildren() {
            return content == null ? emptyList() : content.stream()
                    .filter(t -> t instanceof Xml.Tag)
                    .map(Tag.class::cast)
                    .collect(toList());
        }

        /**
         * Locate an child tag with the given name and set its text value.
         *
         * @param childName The child tag to locate. This assumes there is one and only one.
         * @param text      The text value to set.
         * @return This tag.
         */
        public Xml.Tag withChildValue(String childName, String text) {
            return getChild(childName)
                    .map(tag -> this.withContent(
                            content == null ?
                                    null :
                                    content.stream()
                                            .map(content -> content == tag ?
                                                    ((Tag) content).withValue(text) :
                                                    content)
                                            .collect(toList())
                    ))
                    .orElse(this);
        }

        /**
         * @return If this tag's content is only character data, consider it the value.
         */
        public Optional<String> getValue() {
            if (content == null) {
                return Optional.empty();
            }
            if (content.size() != 1) {
                return Optional.empty();
            }
            if (content.get(0) instanceof Xml.CharData) {
                return Optional.ofNullable(((CharData) content.get(0)).getText());
            }
            return Optional.empty();
        }

        /**
         * A shortcut for {@link #getChild(String)} and {@link #getValue()}.
         *
         * @param name The name of the child element to look for.
         * @return The character data of the first child element matching the provided name, if any.
         */
        public Optional<String> getChildValue(String name) {
            return getChild(name).flatMap(Tag::getValue);
        }

        public Optional<Tag> getSibling(String name, Cursor cursor) {
            if (cursor.getParent() == null) {
                return Optional.empty();
            }
            Xml.Tag parent = cursor.getParent().getValue();
            return parent.getChild(name);
        }

        public Tag withContent(@Nullable List<? extends Content> content) {
            if (this.content == content) {
                return this;
            }

            Tag tag = new Tag(id, prefix, markers, name, attributes, content, closing,
                    beforeTagDelimiterPrefix);

            if (closing == null) {
                if (content != null && !content.isEmpty()) {
                    // TODO test this
                    String indentedClosingTagPrefix = prefix.substring(Math.max(0, prefix.lastIndexOf('\n')));

                    if (content.get(0) instanceof CharData) {
                        return tag.withClosing(new Closing(randomId(),
                                content.get(0).getPrefix().contains("\n") ?
                                        indentedClosingTagPrefix : "",
                                Markers.EMPTY,
                                name, ""));
                    } else {
                        return tag.withClosing(new Closing(randomId(),
                                indentedClosingTagPrefix, Markers.EMPTY,
                                name, ""));
                    }
                }
            }

            return tag;
        }

        @With
        @Nullable
        Closing closing;

        /**
         * Space before '&gt;' or '/&gt;'
         */
        @With
        String beforeTagDelimiterPrefix;

        @Override
        public <P> Xml acceptXml(XmlVisitor<P> v, P p) {
            return v.visitTag(this, p);
        }

        @Override
        public String toString() {
            return "<" + name + attributes.stream().map(a -> a.getKey() + "=...")
                    .collect(Collectors.joining(" ")) + ">";
        }

        @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
        @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
        @Data
        @With
        public static class Closing implements Xml {
            @EqualsAndHashCode.Include
            UUID id;

            String prefix;
            Markers markers;
            String name;

            /**
             * Space before '&gt;'
             */
            String beforeTagDelimiterPrefix;
        }
    }

    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    @Data
    @With
    class Attribute implements Xml {
        @EqualsAndHashCode.Include
        UUID id;

        String prefix;
        Markers markers;
        Ident key;
        String beforeEquals;
        Value value;

        @Override
        public <P> Xml acceptXml(XmlVisitor<P> v, P p) {
            return v.visitAttribute(this, p);
        }

        @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
        @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
        @Data
        @With
        public static class Value implements Xml {
            public enum Quote {
                Double, Single
            }

            @EqualsAndHashCode.Include
            UUID id;

            String prefix;
            Markers markers;
            Quote quote;
            String value;
        }

        public String getKeyAsString() {
            return key.getName();
        }

        public String getValueAsString() {
            return value.getValue();
        }
    }

    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    @Data
    @With
    class CharData implements Xml, Content {
        @EqualsAndHashCode.Include
        UUID id;

        String prefix;
        Markers markers;
        boolean cdata;
        String text;
        String afterText;

        @Override
        public <P> Xml acceptXml(XmlVisitor<P> v, P p) {
            return v.visitCharData(this, p);
        }
    }

    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    @Data
    @With
    class Comment implements Xml, Content, Misc {
        @EqualsAndHashCode.Include
        UUID id;

        String prefix;
        Markers markers;
        String text;

        @Override
        public <P> Xml acceptXml(XmlVisitor<P> v, P p) {
            return v.visitComment(this, p);
        }
    }

    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    @Data
    @With
    class DocTypeDecl implements Xml, Misc {
        @EqualsAndHashCode.Include
        UUID id;

        String prefix;
        Markers markers;
        Ident name;
        Ident externalId;
        List<Ident> internalSubset;

        @Nullable
        ExternalSubsets externalSubsets;

        /**
         * Space before '&gt;'.
         */
        String beforeTagDelimiterPrefix;

        @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
        @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
        @Data
        @With
        public static class ExternalSubsets implements Xml {
            @EqualsAndHashCode.Include
            UUID id;

            String prefix;
            Markers markers;
            List<Element> elements;
        }

        @Override
        public <P> Xml acceptXml(XmlVisitor<P> v, P p) {
            return v.visitDocTypeDecl(this, p);
        }
    }

    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    @Data
    @With
    class Element implements Xml {
        @EqualsAndHashCode.Include
        UUID id;

        String prefix;
        Markers markers;
        List<Ident> subset;

        /**
         * Space before '&gt;'
         */
        @With
        String beforeTagDelimiterPrefix;

        @Override
        public <P> Xml acceptXml(XmlVisitor<P> v, P p) {
            return v.visitElement(this, p);
        }
    }

    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    @Data
    @With
    class Ident implements Xml {
        @EqualsAndHashCode.Include
        UUID id;

        String prefix;
        Markers markers;
        String name;

        @Override
        public <P> Xml acceptXml(XmlVisitor<P> v, P p) {
            return v.visitIdent(this, p);
        }

        @Override
        public String toString() {
            return "Ident{" + name + "}";
        }
    }
}
