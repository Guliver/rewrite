// Generated from /Users/jon/Projects/github/openrewrite/rewrite/c-family-languages/rewrite-family-c/src/main/antlr/MethodSignatureParser.g4 by ANTLR 4.9.2
package org.openrewrite.family.c.internal.grammar;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link MethodSignatureParser}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for
 * operations with no return type.
 */
public interface MethodSignatureParserVisitor<T> extends ParseTreeVisitor<T> {
	/**
	 * Visit a parse tree produced by {@link MethodSignatureParser#methodPattern}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMethodPattern(MethodSignatureParser.MethodPatternContext ctx);
	/**
	 * Visit a parse tree produced by {@link MethodSignatureParser#formalParametersPattern}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFormalParametersPattern(MethodSignatureParser.FormalParametersPatternContext ctx);
	/**
	 * Visit a parse tree produced by {@link MethodSignatureParser#formalsPattern}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFormalsPattern(MethodSignatureParser.FormalsPatternContext ctx);
	/**
	 * Visit a parse tree produced by {@link MethodSignatureParser#dotDot}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDotDot(MethodSignatureParser.DotDotContext ctx);
	/**
	 * Visit a parse tree produced by {@link MethodSignatureParser#formalsPatternAfterDotDot}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFormalsPatternAfterDotDot(MethodSignatureParser.FormalsPatternAfterDotDotContext ctx);
	/**
	 * Visit a parse tree produced by {@link MethodSignatureParser#optionalParensTypePattern}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitOptionalParensTypePattern(MethodSignatureParser.OptionalParensTypePatternContext ctx);
	/**
	 * Visit a parse tree produced by {@link MethodSignatureParser#targetTypePattern}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTargetTypePattern(MethodSignatureParser.TargetTypePatternContext ctx);
	/**
	 * Visit a parse tree produced by {@link MethodSignatureParser#formalTypePattern}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFormalTypePattern(MethodSignatureParser.FormalTypePatternContext ctx);
	/**
	 * Visit a parse tree produced by {@link MethodSignatureParser#classNameOrInterface}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitClassNameOrInterface(MethodSignatureParser.ClassNameOrInterfaceContext ctx);
	/**
	 * Visit a parse tree produced by {@link MethodSignatureParser#simpleNamePattern}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSimpleNamePattern(MethodSignatureParser.SimpleNamePatternContext ctx);
}