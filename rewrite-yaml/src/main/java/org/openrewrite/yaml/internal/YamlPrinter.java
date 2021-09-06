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
package org.openrewrite.yaml.internal;

import org.openrewrite.PrintOutputCapture;
import org.openrewrite.yaml.YamlVisitor;
import org.openrewrite.yaml.tree.Yaml;

public class YamlPrinter<P> extends YamlVisitor<PrintOutputCapture<P>> {

    @Override
    public Yaml visitDocument(Yaml.Document document, PrintOutputCapture<P> p) {
        p.out.append(document.getPrefix());
        visitMarkers(document.getMarkers(), p);
        if (document.isExplicit()) {
            p.out.append("---");
        }
        visit(document.getBlock(), p);
        p.out.append(document.getEnd().getPrefix());
        if(document.getEnd().isExplicit()) {
            p.out.append("...");
        }
        return document;
    }

    @Override
    public Yaml visitDocuments(Yaml.Documents documents, PrintOutputCapture<P> p) {
        visitMarkers(documents.getMarkers(), p);
        visit(documents.getDocuments(), p);
        return documents;
    }

    @Override
    public Yaml visitSequenceEntry(Yaml.Sequence.Entry entry, PrintOutputCapture<P> p) {
        p.out.append(entry.getPrefix());
        if(entry.isDash()) {
            p.out.append('-');
        }
        visit(entry.getBlock(), p);
        if(entry.getTrailingCommaPrefix() != null) {
            p.out.append(entry.getTrailingCommaPrefix()).append(',');
        }
        return entry;
    }

    @Override
    public Yaml visitSequence(Yaml.Sequence sequence, PrintOutputCapture<P> p) {
        visitMarkers(sequence.getMarkers(), p);
        if(sequence.getOpeningBracketPrefix() != null) {
            p.out.append(sequence.getOpeningBracketPrefix()).append('[');
        }
        Yaml result = super.visitSequence(sequence, p);
        if(sequence.getClosingBracketPrefix() != null) {
            p.out.append(sequence.getClosingBracketPrefix()).append(']');
        }

        return result;
    }

    @Override
    public Yaml visitMappingEntry(Yaml.Mapping.Entry entry, PrintOutputCapture<P> p) {
        p.out.append(entry.getPrefix());
        visitMarkers(entry.getMarkers(), p);
        visit(entry.getKey(), p);
        p.out.append(entry.getBeforeMappingValueIndicator()).append(':');
        visit(entry.getValue(), p);
        return entry;
    }

    @Override
    public Yaml visitMapping(Yaml.Mapping mapping, PrintOutputCapture<P> p) {
        visitMarkers(mapping.getMarkers(), p);
        return super.visitMapping(mapping, p);
    }

    @Override
    public Yaml visitScalar(Yaml.Scalar scalar, PrintOutputCapture<P> p) {
        p.out.append(scalar.getPrefix());
        visitMarkers(scalar.getMarkers(), p);
        switch (scalar.getStyle()) {
            case DOUBLE_QUOTED:
                p.out.append('"')
                        .append(scalar.getValue().replaceAll("\\n", "\\\\n"))
                        .append('"');
                break;
            case SINGLE_QUOTED:
                p.out.append('\'')
                        .append(scalar.getValue().replaceAll("\\n", "\\\\n"))
                        .append('\'');
                break;
            case LITERAL:
                p.out.append('|')
                        .append(scalar.getValue());
                break;
            case FOLDED:
                p.out.append('>')
                        .append(scalar.getValue());
                break;
            case PLAIN:
            default:
                p.out.append(scalar.getValue());
                break;

        }
        return scalar;
    }
}
