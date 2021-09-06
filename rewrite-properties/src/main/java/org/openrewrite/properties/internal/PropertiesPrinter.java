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
package org.openrewrite.properties.internal;

import org.openrewrite.PrintOutputCapture;
import org.openrewrite.properties.PropertiesVisitor;
import org.openrewrite.properties.tree.Properties;

public class PropertiesPrinter<P> extends PropertiesVisitor<PrintOutputCapture<P>> {

    @Override
    public Properties visitFile(Properties.File file, PrintOutputCapture<P> p) {
        p.out.append(file.getPrefix());
        visit(file.getContent(), p);
        p.out.append(file.getEof());
        return file;
    }

    @Override
    public Properties visitEntry(Properties.Entry entry, PrintOutputCapture<P> p) {
        p.out.append(entry.getPrefix())
                .append(entry.getKey())
                .append(entry.getBeforeEquals())
                .append('=')
                .append(entry.getValue().getPrefix())
                .append(entry.getValue().getText());
        return entry;
    }

    @Override
    public Properties visitComment(Properties.Comment comment, PrintOutputCapture<P> p) {
        p.out.append(comment.getPrefix())
                .append('#')
                .append(comment.getMessage());
        return comment;
    }
}
