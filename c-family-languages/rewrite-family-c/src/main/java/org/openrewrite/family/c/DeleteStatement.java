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
package org.openrewrite.family.c;

import org.openrewrite.internal.ListUtils;
import org.openrewrite.family.c.search.FindReferencedTypes;
import org.openrewrite.family.c.tree.C;
import org.openrewrite.family.c.tree.CType;
import org.openrewrite.family.c.tree.Space;
import org.openrewrite.family.c.tree.Statement;
import org.openrewrite.marker.Markers;

import static java.util.Collections.emptyList;
import static org.openrewrite.Tree.randomId;

/**
 * Deletes standalone statements. Does not include deletion of control statements present in for loops.
 */
public class DeleteStatement<P> extends CIsoVisitor<P> {
    private final Statement statement;

    public DeleteStatement(Statement statement) {
        this.statement = statement;
    }

    @Override
    public C.If visitIf(C.If iff, P p) {
        C.If i = super.visitIf(iff, p);

        if (statement.isScope(i.getThenPart())) {
            i = i.withThenPart(emptyBlock());
        } else if (i.getElsePart() != null && statement.isScope(i.getElsePart())) {
            i = i.withElsePart(i.getElsePart().withBody(emptyBlock()));
        }

        return i;
    }

    @Override
    public C.ForLoop visitForLoop(C.ForLoop forLoop, P p) {
        return statement.isScope(forLoop.getBody()) ?
                forLoop.withBody(emptyBlock()) :
                super.visitForLoop(forLoop, p);
    }

    @Override
    public C.ForEachLoop visitForEachLoop(C.ForEachLoop forEachLoop, P p) {
        return statement.isScope(forEachLoop.getBody()) ?
                forEachLoop.withBody(emptyBlock()) :
                super.visitForEachLoop(forEachLoop, p);
    }

    @Override
    public C.WhileLoop visitWhileLoop(C.WhileLoop whileLoop, P p) {
        return statement.isScope(whileLoop.getBody()) ? whileLoop.withBody(emptyBlock()) :
                super.visitWhileLoop(whileLoop, p);
    }

    @Override
    public C.DoWhileLoop visitDoWhileLoop(C.DoWhileLoop doWhileLoop, P p) {
        return statement.isScope(doWhileLoop.getBody()) ? doWhileLoop.withBody(emptyBlock()) :
                super.visitDoWhileLoop(doWhileLoop, p);
    }

    @Override
    public C.Block visitBlock(C.Block block, P p) {
        C.Block b = super.visitBlock(block, p);
        return b.withStatements(ListUtils.map(b.getStatements(), s ->
                statement.isScope(s) ? null : s));
    }

    @Override
    public C preVisit(C tree, P p) {
        if (statement.isScope(tree)) {
            for (CType.FullyQualified referenced : FindReferencedTypes.find(tree)) {
                maybeRemoveImport(referenced);
            }
        }
        return super.preVisit(tree, p);
    }

    private Statement emptyBlock() {
        return new C.Block(randomId(),
                Space.EMPTY,
                Markers.EMPTY,
                null,
                emptyList(),
                Space.EMPTY
        );
    }
}
