// Generated from /Users/jon/Projects/github/openrewrite/rewrite/c-family-languages/rewrite-family-c/src/main/antlr/TemplateParameterParser.g4 by ANTLR 4.9.2
package org.openrewrite.family.c.internal.grammar;
import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link TemplateParameterParser}.
 */
public interface TemplateParameterParserListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by {@link TemplateParameterParser#matcherPattern}.
	 * @param ctx the parse tree
	 */
	void enterMatcherPattern(TemplateParameterParser.MatcherPatternContext ctx);
	/**
	 * Exit a parse tree produced by {@link TemplateParameterParser#matcherPattern}.
	 * @param ctx the parse tree
	 */
	void exitMatcherPattern(TemplateParameterParser.MatcherPatternContext ctx);
	/**
	 * Enter a parse tree produced by {@link TemplateParameterParser#matcherParameter}.
	 * @param ctx the parse tree
	 */
	void enterMatcherParameter(TemplateParameterParser.MatcherParameterContext ctx);
	/**
	 * Exit a parse tree produced by {@link TemplateParameterParser#matcherParameter}.
	 * @param ctx the parse tree
	 */
	void exitMatcherParameter(TemplateParameterParser.MatcherParameterContext ctx);
	/**
	 * Enter a parse tree produced by {@link TemplateParameterParser#matcherName}.
	 * @param ctx the parse tree
	 */
	void enterMatcherName(TemplateParameterParser.MatcherNameContext ctx);
	/**
	 * Exit a parse tree produced by {@link TemplateParameterParser#matcherName}.
	 * @param ctx the parse tree
	 */
	void exitMatcherName(TemplateParameterParser.MatcherNameContext ctx);
}