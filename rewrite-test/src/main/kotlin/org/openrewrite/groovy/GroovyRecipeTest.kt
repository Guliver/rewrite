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
package org.openrewrite.groovy

import org.intellij.lang.annotations.Language
import org.openrewrite.Recipe
import org.openrewrite.RecipeTest
import org.openrewrite.java.TypeValidator
import org.openrewrite.java.tree.JavaSourceFile
import java.io.File

interface GroovyRecipeTest : RecipeTest<JavaSourceFile> {
    override val parser: GroovyParser
        get() = GroovyParser.builder().build()

    fun assertChanged(
        recipe: Recipe = this.recipe!!,
        moderneAstLink: String,
        moderneApiBearerToken: String = apiTokenFromUserHome(),
        @Language("groovy") after: String,
        cycles: Int = 2,
        expectedCyclesThatMakeChanges: Int = cycles - 1,
        afterConditions: (JavaSourceFile) -> Unit = { }
    ) {
        super.assertChangedBase(recipe, moderneAstLink, moderneApiBearerToken, after, cycles, expectedCyclesThatMakeChanges, afterConditions)
    }

    fun assertChanged(
        parser: GroovyParser = this.parser,
        recipe: Recipe = this.recipe!!,
        @Language("groovy") before: String,
        @Language("groovy") dependsOn: Array<String> = emptyArray(),
        @Language("groovy") after: String,
        cycles: Int = 2,
        expectedCyclesThatMakeChanges: Int = cycles - 1,
        skipEnhancedTypeValidation: Boolean = false,
        afterConditions: (JavaSourceFile) -> Unit = { }
    ) {
        val typeValidatingAfterConditions: (JavaSourceFile) -> Unit = if(skipEnhancedTypeValidation) {
            afterConditions
        } else {
            {
                TypeValidator.assertTypesValid(it)
                afterConditions(it)
            }
        }
        super.assertChangedBase(parser, recipe, before, dependsOn, after, cycles, expectedCyclesThatMakeChanges, typeValidatingAfterConditions)
    }

    fun assertChanged(
        parser: GroovyParser = this.parser,
        recipe: Recipe = this.recipe!!,
        @Language("groovy") before: File,
        @Language("groovy") dependsOn: Array<File> = emptyArray(),
        @Language("groovy") after: String,
        cycles: Int = 2,
        expectedCyclesThatMakeChanges: Int = cycles - 1,
        skipEnhancedTypeValidation: Boolean = false,
        afterConditions: (JavaSourceFile) -> Unit = { }
    ) {
        val typeValidatingAfterConditions: (JavaSourceFile) -> Unit = if(skipEnhancedTypeValidation) {
            afterConditions
        } else {
            {
                TypeValidator.assertTypesValid(it)
                afterConditions(it)
            }
        }
        super.assertChangedBase(parser, recipe, before, dependsOn, after, cycles, expectedCyclesThatMakeChanges, typeValidatingAfterConditions)
    }

    fun assertUnchanged(
        parser: GroovyParser = this.parser,
        recipe: Recipe = this.recipe!!,
        @Language("groovy") before: String,
        @Language("groovy") dependsOn: Array<String> = emptyArray()
    ) {
        super.assertUnchangedBase(parser, recipe, before, dependsOn)
    }

    fun assertUnchanged(
        parser: GroovyParser = this.parser,
        recipe: Recipe = this.recipe!!,
        @Language("groovy") before: File,
        @Language("groovy") dependsOn: Array<File> = emptyArray()
    ) {
        super.assertUnchangedBase(parser, recipe, before, dependsOn)
    }
}
