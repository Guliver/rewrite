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

import org.openrewrite.Cursor;
import org.openrewrite.Incubating;
import org.openrewrite.Tree;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.family.c.tree.C;
import org.openrewrite.family.c.tree.Statement;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

/**
 * Renames a NamedVariable to the target name.
 * Prevents variables from being renamed to reserved java keywords.
 *
 * Notes:
 *  - The current version will rename variables even if a variable with `toName` is already declared in the same scope.
 *  - FieldAccess to the new variable need to be covered separately. Refer to ChangeFieldAccess.
 */
@Incubating(since = "7.5.0")
public class RenameVariable<P> extends CIsoVisitor<P> {
    private final C.VariableDeclarations.NamedVariable variable;
    private final String toName;

    public RenameVariable(C.VariableDeclarations.NamedVariable variable, String toName) {
        this.variable = variable;
        this.toName = toName;
    }

    @Override
    public C.VariableDeclarations.NamedVariable visitVariable(C.VariableDeclarations.NamedVariable variable, P p) {
        if (!JavaKeywords.isReserved(toName) && !StringUtils.isBlank(toName) && variable.equals(this.variable)) {
            doAfterVisit(new RenameVariableByCursor(getCursor()));
            return variable;
        }
        return super.visitVariable(variable, p);
    }

    private class RenameVariableByCursor extends CIsoVisitor<P> {
        private final Cursor scope;
        private final Stack<Tree> currentNameScope = new Stack<>();

        public RenameVariableByCursor(Cursor scope) {
            this.scope = scope;
        }

        @Override
        public C.Block visitBlock(C.Block block, P p) {
            // A variable name scope is owned by the ClassDeclaration regardless of what order it is declared.
            // Variables declared in a child block are owned by the corresponding block until the block is exited.
            if (getCursor().getParent() != null && getCursor().getParent().getValue() instanceof C.ClassDeclaration) {
                boolean isClassScope = false;
                for (Statement statement : block.getStatements()) {
                    if (statement instanceof C.VariableDeclarations) {
                        if (((C.VariableDeclarations) statement).getVariables().contains(variable)) {
                            isClassScope = true;
                            break;
                        }
                    }
                }

                if (isClassScope) {
                    currentNameScope.add(block);
                }
            }
            return super.visitBlock(block, p);
        }

        @Override
        public C.Identifier visitIdentifier(C.Identifier ident, P p) {
            // The size of the stack will be 1 if the identifier is in the right scope.
            if (ident.getSimpleName().equals(variable.getSimpleName()) && isInSameNameScope(scope, getCursor()) && currentNameScope.size() == 1) {
                if (!(getCursor().dropParentUntil(C.class::isInstance).getValue() instanceof C.FieldAccess)) {
                    return ident.withName(toName);
                }
            }
            return super.visitIdentifier(ident, p);
        }

        @Override
        public C.VariableDeclarations.NamedVariable visitVariable(C.VariableDeclarations.NamedVariable namedVariable, P p) {
            if (namedVariable.getSimpleName().equals(variable.getSimpleName())) {
                Cursor parentScope = getCursorToParentScope(getCursor());
                // The target variable was found and was not declared in a class declaration block.
                if (currentNameScope.isEmpty()) {
                    if (namedVariable.equals(variable)) {
                        currentNameScope.add(parentScope.getValue());
                    }
                } else {
                    // A variable has been declared and created a new name scope.
                    if (!parentScope.getValue().equals(currentNameScope.peek()) && getCursor().isScopeInPath(currentNameScope.peek())) {
                        currentNameScope.add(parentScope.getValue());
                    }
                }
            }
            return super.visitVariable(namedVariable, p);
        }

        @Override
        public C.Try.Catch visitCatch(C.Try.Catch _catch, P p) {
            // Check if the try added a new scope to the stack,
            // postVisit doesn't happen until after both the try and catch are processed.
            maybeChangeNameScope(getCursorToParentScope(getCursor()).getValue());
            return super.visitCatch(_catch, p);
        }

        @Override
        public C.MultiCatch visitMultiCatch(C.MultiCatch multiCatch, P p) {
            // Check if the try added a new scope to the stack,
            // postVisit doesn't happen until after both the try and catch are processed.
            maybeChangeNameScope(getCursorToParentScope(getCursor()).getValue());
            return super.visitMultiCatch(multiCatch, p);
        }

        @Nullable
        @Override
        public C postVisit(C tree, P p) {
            maybeChangeNameScope(tree);
            return super.postVisit(tree, p);
        }

        /**
         * Used to check if the name scope has changed.
         * Pops the stack if the tree element is at the top of the stack.
         */
        private void maybeChangeNameScope(Tree tree) {
            if (currentNameScope.size() > 0 && currentNameScope.peek().equals(tree)) {
                currentNameScope.pop();
            }
        }

        /**
         * Returns either the current block or a J.Type that may create a reference to a variable.
         * I.E. for(int target = 0; target < N; target++) creates a new name scope for `target`.
         * The name scope in the next J.Block `{}` cannot create new variables with the name `target`.
         * <p>
         * J.* types that may only reference an existing name and do not create a new name scope are excluded.
         */
        private Cursor getCursorToParentScope(Cursor cursor) {
            return cursor.dropParentUntil(is ->
                    is instanceof C.Block ||
                            is instanceof C.MethodDeclaration ||
                            is instanceof C.ForLoop ||
                            is instanceof C.ForEachLoop ||
                            is instanceof C.Case ||
                            is instanceof C.Try ||
                            is instanceof C.Try.Catch ||
                            is instanceof C.MultiCatch ||
                            is instanceof C.Lambda
            );
        }
    }

    private static final class JavaKeywords {
        JavaKeywords() {}

        private static final String[] RESERVED_WORDS = new String[] {
                "abstract",
                "assert",
                "boolean",
                "break",
                "byte",
                "case",
                "catch",
                "char",
                "class",
                "const",
                "continue",
                "default",
                "do",
                "double",
                "else",
                "enum",
                "extends",
                "final",
                "finally",
                "float",
                "for",
                "goto",
                "if",
                "implements",
                "import",
                "instanceof",
                "int",
                "interface",
                "long",
                "native",
                "new",
                "package",
                "private",
                "protected",
                "public",
                "return",
                "short",
                "static",
                "strictfp",
                "super",
                "switch",
                "synchronized",
                "this",
                "throw",
                "throws",
                "transient",
                "try",
                "void",
                "volatile",
                "while",
        };

        private static final Set<String> RESERVED_WORDS_SET = new HashSet<>(Arrays.asList(RESERVED_WORDS));

        public static boolean isReserved(String word) {
            return RESERVED_WORDS_SET.contains(word);
        }
    }
}
