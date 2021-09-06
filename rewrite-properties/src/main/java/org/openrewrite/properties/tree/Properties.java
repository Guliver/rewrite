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
package org.openrewrite.properties.tree;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.With;
import lombok.experimental.FieldDefaults;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.marker.Markers;
import org.openrewrite.properties.PropertiesVisitor;

import java.io.Serializable;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class, property = "@ref")
public interface Properties extends Serializable, Tree {

    @SuppressWarnings("unchecked")
    @Override
    default <R extends Tree, P> R accept(TreeVisitor<R, P> v, P p) {
        return (R) acceptProperties((PropertiesVisitor<P>) v, p);
    }

    @Override
    default <P> boolean isAcceptable(TreeVisitor<?, P> v, P p) {
        return v instanceof PropertiesVisitor;
    }

    @Nullable
    default <P> Properties acceptProperties(PropertiesVisitor<P> v, P p) {
        return v.defaultValue(this, p);
    }

    String getPrefix();

    Properties withPrefix(String prefix);

    <T extends Properties> T withMarkers(Markers markers);

    Markers getMarkers();

    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    @Data
    @With
    class File implements Properties, SourceFile {
        @EqualsAndHashCode.Include
        UUID id;

        String prefix;
        Markers markers;
        Path sourcePath;
        List<Content> content;
        String eof;

        @Override
        public <P> Properties acceptProperties(PropertiesVisitor<P> v, P p) {
            return v.visitFile(this, p);
        }
    }

    interface Content extends Properties {
    }

    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    @Data
    @With
    class Entry implements Content {
        @EqualsAndHashCode.Include
        UUID id;

        String prefix;
        Markers markers;
        String key;
        String beforeEquals;
        Value value;

        @Override
        public <P> Properties acceptProperties(PropertiesVisitor<P> v, P p) {
            return v.visitEntry(this, p);
        }
    }

    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    @Data
    class Value {
        @EqualsAndHashCode.Include
        UUID id;

        @With
        String prefix;

        @With
        Markers markers;

        @With
        String text;
    }

    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    @Data
    @With
    class Comment implements Content {
        @EqualsAndHashCode.Include
        UUID id;

        String prefix;
        Markers markers;
        String message;

        @Override
        public <P> Properties acceptProperties(PropertiesVisitor<P> v, P p) {
            return v.visitComment(this, p);
        }
    }
}
