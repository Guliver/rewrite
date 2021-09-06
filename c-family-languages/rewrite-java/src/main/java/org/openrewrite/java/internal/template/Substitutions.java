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
package org.openrewrite.java.internal.template;

import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.*;
import org.openrewrite.family.c.tree.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.PropertyPlaceholderHelper;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.internal.grammar.TemplateParameterLexer;
import org.openrewrite.java.internal.grammar.TemplateParameterParser;
import org.openrewrite.java.tree.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RequiredArgsConstructor
public class Substitutions {
    private static final Pattern PATTERN_COMMENT = Pattern.compile("__p(\\d+)__");

    private final String code;
    private final Object[] parameters;
    private final PropertyPlaceholderHelper propertyPlaceholderHelper = new PropertyPlaceholderHelper(
            "#{", "}", null);

    public String substitute() {
        AtomicInteger index = new AtomicInteger(0);
        String substituted = code;
        while(true) {
            String previous = substituted;
            substituted = propertyPlaceholderHelper.replacePlaceholders(substituted, key -> {
                int i = index.getAndIncrement();
                Object parameter = parameters[i];

                String s;
                if (!key.isEmpty()) {
                    TemplateParameterParser parser = new TemplateParameterParser(new CommonTokenStream(new TemplateParameterLexer(
                            CharStreams.fromString(key))));

                    parser.removeErrorListeners();
                    parser.addErrorListener(new ThrowingErrorListener());

                    TemplateParameterParser.MatcherPatternContext ctx = parser.matcherPattern();
                    String matcherName = ctx.matcherName().Identifier().getText();
                    List<TemplateParameterParser.MatcherParameterContext> params = ctx.matcherParameter();

                    if ("anyArray".equals(matcherName)) {
                        if (!(parameter instanceof TypedTree)) {
                            throw new IllegalArgumentException("anyArray can only be used on TypedTree parameters");
                        }

                        CType type = ((TypedTree) parameter).getType();
                        CType.Array arrayType = TypeUtils.asArray(type);
                        if (arrayType == null) {
                            CType.Method methodType = TypeUtils.asMethod(type);
                            arrayType = TypeUtils.asArray(methodType == null ? null : methodType.getResolvedSignature().getReturnType());
                            if (arrayType == null) {
                                throw new IllegalArgumentException("anyArray can only be used on parameters containing CType.Array type attribution");
                            }
                        }

                        s = "(/*__p" + i + "__*/new ";

                        StringBuilder extraDim = new StringBuilder();
                        for (; arrayType.getElemType() instanceof CType.Array; arrayType = (CType.Array) arrayType.getElemType()) {
                            extraDim.append("[0]");
                        }

                        if (arrayType.getElemType() instanceof CType.Primitive) {
                            s += ((CType.Primitive) arrayType.getElemType()).getKeyword();
                        } else if (arrayType.getElemType() instanceof CType.FullyQualified) {
                            s += ((CType.FullyQualified) arrayType.getElemType()).getFullyQualifiedName();
                        }

                        s += "[0]" + extraDim + ")";
                    } else if ("any".equals(matcherName)) {
                        String fqn;

                        if (params.size() == 1) {
                            fqn = params.get(0).FullyQualifiedName().getText();
                        } else {
                            if (!(parameter instanceof TypedTree)) {
                                // any should only be used on TypedTree parameters, but will give it a best effort
                                fqn = "java.lang.Object";
                            } else {
                                fqn = getTypeName(((TypedTree) parameter).getType());
                            }
                        }

                        CType.Primitive primitive = CType.Primitive.fromKeyword(fqn);
                        if (primitive != null) {
                            s = "(/*__p" + i + "__*/";
                            switch (primitive) {
                                case Boolean:
                                    s += "true";
                                    break;
                                case Double:
                                case Float:
                                case Int:
                                case Long:
                                    s += "0";
                                    break;
                                case Short:
                                    s += "(short)0";
                                    break;
                                case Byte:
                                    s += "(byte)0";
                                    break;
                                case Char:
                                    s += "'\0'";
                                    break;
                                case String:
                                    s += "\"\"";
                                    break;
                            }
                            s += ")";
                        } else if ("Object".equals(fqn)) {
                            s = "(/*__p" + i + "__*/null)";
                        } else {
                            s = "(/*__p" + i + "__*/(" + fqn + ")null)";
                        }
                        parameters[i] = ((J) parameter).withPrefix(Space.EMPTY);
                    } else {
                        throw new IllegalArgumentException("Invalid template matcher '" + key + "'");
                    }
                } else {
                    s = substituteSingle(parameter, i);
                }

                return s;
            });

            if(previous.equals(substituted)) {
                break;
            }
        }

        return substituted;
    }

    private String getTypeName(@Nullable CType type) {
        if (type == null) {
            return "java.lang.Object";
        } else if (type instanceof CType.FullyQualified) {
            return ((CType.FullyQualified) type).getFullyQualifiedName();
        } else if (type instanceof CType.Primitive) {
            return ((CType.Primitive) type).getKeyword();
        } else if(type instanceof CType.Method) {
            return getTypeName(((CType.Method) type).getResolvedSignature().getReturnType());
        } else {
            return "java.lang.Object";
        }
    }

    private String substituteSingle(Object parameter, int index) {
        if (parameter instanceof C) {
            if (parameter instanceof C.Annotation) {
                return "@SubAnnotation(" + index + ")";
            } else if (parameter instanceof C.Block) {
                return "/*__p" + index + "__*/{}";
            } else if (parameter instanceof C.Literal || parameter instanceof C.VariableDeclarations) {
                return J.printTrimmed((C) parameter);
            } else {
                throw new IllegalArgumentException("'" + parameter.getClass().getSimpleName() + "' cannot be a parameter to a template.");
            }
        } else if (parameter instanceof CRightPadded) {
            return substituteSingle(((CRightPadded<?>) parameter).getElement(), index);
        } else if (parameter instanceof CLeftPadded) {
            return substituteSingle(((CLeftPadded<?>) parameter).getElement(), index);
        }
        return parameter.toString();
    }

    public <C2 extends C> List<C2> unsubstitute(List<C2> js) {
        return ListUtils.map(js, this::unsubstitute);
    }

    public <C2 extends C> C2 unsubstitute(C2 j) {
        if (parameters.length == 0) {
            return j;
        }

        //noinspection unchecked
        C2 unsub = (C2) new JavaVisitor<Integer>() {
            @SuppressWarnings("ConstantConditions")
            @Override
            public C visitAnnotation(J.Annotation annotation, Integer integer) {
                if (TypeUtils.isOfClassType(annotation.getType(), "SubAnnotation")) {
                    J.Literal index = (J.Literal) annotation.getArguments().get(0);
                    J a2 = (J) parameters[(Integer) index.getValue()];
                    return a2.withPrefix(a2.getPrefix().withWhitespace(annotation.getPrefix().getWhitespace()));
                }
                return super.visitAnnotation(annotation, integer);
            }

            @Override
            public C visitBlock(J.Block block, Integer integer) {
                C param = maybeParameter(block);
                if (param != null) {
                    return param;
                }
                return super.visitBlock(block, integer);
            }

            @Override
            public <T extends C> C visitParentheses(C.Parentheses<T> parens, Integer integer) {
                C param = maybeParameter(parens.getTree());
                if (param != null) {
                    return param;
                }
                return super.visitParentheses(parens, integer);
            }

            @Override
            public C visitLiteral(J.Literal literal, Integer integer) {
                C param = maybeParameter(literal);
                if (param != null) {
                    return param;
                }
                return super.visitLiteral(literal, integer);
            }

            @Nullable
            private C maybeParameter(C j) {
                Integer param = parameterIndex(j.getPrefix());
                if (param != null) {
                    J j2 = (J) parameters[param];
                    return j2.withPrefix(j2.getPrefix().withWhitespace(j.getPrefix().getWhitespace()));
                }
                return null;
            }

            @Nullable
            private Integer parameterIndex(Space space) {
                for (Comment comment : space.getComments()) {
                    if(comment instanceof TextComment) {
                        Matcher matcher = PATTERN_COMMENT.matcher(((TextComment) comment).getText());
                        if (matcher.matches()) {
                            return Integer.valueOf(matcher.group(1));
                        }
                    }
                }
                return null;
            }
        }.visit(j, 0);

        assert unsub != null;
        return unsub;
    }

    private static class ThrowingErrorListener extends BaseErrorListener {
        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                int line, int charPositionInLine, String msg, RecognitionException e) {
            throw new IllegalArgumentException(
                    String.format("Syntax error at line %d:%d %s.", line, charPositionInLine, msg), e);
        }
    }
}
