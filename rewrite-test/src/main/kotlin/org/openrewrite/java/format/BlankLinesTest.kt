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
package org.openrewrite.java.format

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.openrewrite.Issue
import org.openrewrite.Recipe
import org.openrewrite.Tree.randomId
import org.openrewrite.family.c.format.AutoFormat
import org.openrewrite.family.c.format.BlankLines
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest
import org.openrewrite.family.c.style.BlankLinesStyle
import org.openrewrite.family.c.style.IntelliJ
import org.openrewrite.style.NamedStyles

interface BlankLinesTest : JavaRecipeTest {
    override val recipe: Recipe
        get() = BlankLines()

    fun blankLines(with: BlankLinesStyle.() -> BlankLinesStyle = { this }) = listOf(
        NamedStyles(
                randomId(), "test", "test", "test", emptySet(), listOf(
                IntelliJ.blankLines().run { with(this) })
        )
    )

    @Issue("https://github.com/openrewrite/rewrite/issues/621")
    @Test
    fun leaveTrailingComments(jp: JavaParser.Builder<*, *>) = assertUnchanged(
        recipe = AutoFormat(),
        parser = jp.styles(blankLines()).build(),
        before = """
            public class A {
                private Long id; // this comment will move to wrong place

                public Long id() {
                    return id;
                }
            }
        """.trimIndent()
    )

    @Issue("https://github.com/openrewrite/rewrite/issues/620")
    @Test
    fun noBlankLineForFirstEnum(jp: JavaParser.Builder<*, *>) = assertUnchanged(
        recipe = AutoFormat(),
        parser = jp.styles(blankLines()).build(),
        before = """
            public enum TheEnum {
                FIRST,
                SECOND
            }
        """
    )

    @Test
    fun eachMethodOnItsOwnLine(jp: JavaParser.Builder<*, *>) = assertChanged(
        jp.styles(blankLines()).build(),
        before = """
            public class Test {
                void a() {
                }    void b() {
                }
            }
        """,
        after = """
            public class Test {
                void a() {
                }
            
                void b() {
                }
            }
        """
    )

    @Test
    fun keepMaximumInDeclarations(jp: JavaParser.Builder<*, *>) = assertChanged(
        jp.styles(blankLines { withKeepMaximum(keepMaximum.withInDeclarations(0)) }).build(),
        before = """
            public class Test {
            
            
                private int field1;
                private int field2;
            
                {
                    field1 = 2;
                }
            
                public void test1() {
                    new Runnable() {
                        public void run() {
                        }
                    };
                }
            
                public class InnerClass {
                }
            }
        """,
        after = """
            public class Test {
                private int field1;
                private int field2;
            
                {
                    field1 = 2;
                }
            
                public void test1() {
                    new Runnable() {
                        public void run() {
                        }
                    };
                }
            
                public class InnerClass {
                }
            }
        """
    )

    @Test
    fun keepMaximumInCode(jp: JavaParser.Builder<*, *>) = assertChanged(
        jp.styles(blankLines { withKeepMaximum(keepMaximum.withInCode(0)) }).build(),
        before = """
            public class Test {
                private int field1;
            
                {


                    field1 = 2;
                }
            }
        """,
        after = """
            public class Test {
                private int field1;
            
                {
                    field1 = 2;
                }
            }
        """
    )

    @Test
    fun keepMaximumBeforeEndOfBlock(jp: JavaParser.Builder<*, *>) = assertChanged(
        jp.styles(blankLines { withKeepMaximum(keepMaximum.withBeforeEndOfBlock(0)) }).build(),
        before = """
            public class Test {
                private int field1;
            
                {
                    field1 = 2;


                }
            }
        """,
        after = """
            public class Test {
                private int field1;
            
                {
                    field1 = 2;
                }
            }
        """
    )

    @Test
    fun keepMaximumBetweenHeaderAndPackage(jp: JavaParser.Builder<*, *>) = assertChanged(
        jp.styles(blankLines {
            withKeepMaximum(
                keepMaximum
                    .withBetweenHeaderAndPackage(0)
            )
        }).build(),
        before = """
            /*
             * This is a sample file.
             */

            package com.intellij.samples;

            public class Test {
            }
        """,
        after = """
            /*
             * This is a sample file.
             */
            package com.intellij.samples;

            public class Test {
            }
        """
    )

    @Test
    fun minimumPackageWithComment(jp: JavaParser.Builder<*, *>) = assertChanged(
        jp.styles(blankLines {
            withKeepMaximum(keepMaximum.withBeforeEndOfBlock(0))
            withMinimum(minimum.withBeforePackage(1)) // this takes precedence over the "keep max"
        }).build(),
        before = """
            /*
             * This is a sample file.
             */
            package com.intellij.samples;

            public class Test {
            }
        """,
        after = """
            /*
             * This is a sample file.
             */

            package com.intellij.samples;

            public class Test {
            }
        """
    )

    @Test
    fun minimumBeforePackage(jp: JavaParser.Builder<*, *>) = assertChanged(
        jp.styles(blankLines {
            withMinimum(minimum.withBeforePackage(1)) // no blank lines if nothing preceding package
        }).build(),
        before = """

            package com.intellij.samples;

            public class Test {
            }
        """,
        after = """
            package com.intellij.samples;

            public class Test {
            }
        """
    )

    @Test
    fun minimumBeforePackageWithComment(jp: JavaParser.Builder<*, *>) = assertChanged(
        jp.styles(blankLines {
            withKeepMaximum(keepMaximum.withBetweenHeaderAndPackage(0))
            withMinimum(minimum.withBeforePackage(1)) // this takes precedence over the "keep max"
        }).build(),
        before = """
            /* Comment */
            package com.intellij.samples;

            public class Test {
            }
        """,
        after = """
            /* Comment */

            package com.intellij.samples;

            public class Test {
            }
        """
    )

    @Test
    fun minimumBeforeImportsWithPackage(jp: JavaParser.Builder<*, *>) = assertChanged(
        jp.styles(blankLines {
            withMinimum(minimum.withBeforeImports(1)) // no blank lines if nothing preceding package
        }).build(),
        before = """
            package com.intellij.samples;
            import java.util.Vector;

            public class Test {
            }
        """,
        after = """
            package com.intellij.samples;

            import java.util.Vector;

            public class Test {
            }
        """
    )

    @Test
    fun minimumBeforeImports(jp: JavaParser.Builder<*, *>) = assertChanged(
        jp.styles(blankLines {
            withMinimum(minimum.withBeforeImports(1)) // no blank lines if nothing preceding package
        }).build(),
        before = """

            import java.util.Vector;

            public class Test {
            }
        """,
        after = """
            import java.util.Vector;

            public class Test {
            }
        """.trimIndent()
    )

    @Test
    fun minimumBeforeImportsWithComment(jp: JavaParser.Builder<*, *>) = assertChanged(
        jp.styles(blankLines {
            withMinimum(minimum.withBeforeImports(1)) // no blank lines if nothing preceding package
        }).build(),
        before = """
            /*
             * This is a sample file.
             */
            import java.util.Vector;

            public class Test {
            }
        """,
        after = """
            /*
             * This is a sample file.
             */

            import java.util.Vector;

            public class Test {
            }
        """
    )

    @Test
    fun minimumAfterPackageWithImport(jp: JavaParser.Builder<*, *>) = assertChanged(
        jp.styles(blankLines {
            withMinimum(
                minimum
                    .withBeforeImports(0)
                    .withAfterPackage(1)
            )
        }).build(),
        before = """
            package com.intellij.samples;
            import java.util.Vector;

            public class Test {
            }
        """,
        after = """
            package com.intellij.samples;

            import java.util.Vector;

            public class Test {
            }
        """
    )

    @Test
    fun minimumAfterPackage(jp: JavaParser.Builder<*, *>) = assertChanged(
        jp.styles(blankLines {
            withMinimum(minimum.withAfterPackage(1))
        }).build(),
        before = """
            package com.intellij.samples;
            public class Test {
            }
        """,
        after = """
            package com.intellij.samples;

            public class Test {
            }
        """
    )

    @Test
    fun minimumAfterImports(jp: JavaParser.Builder<*, *>) = assertChanged(
        jp.styles(blankLines {
            withMinimum(minimum.withAfterImports(1))
        }).build(),
        before = """
            import java.util.Vector;
            public class Test {
            }
        """,
        after = """
            import java.util.Vector;

            public class Test {
            }
        """.trimIndent()
    )

    @Test
    fun noImportsNoPackage(jp: JavaParser) = assertChanged(
        jp,
        before = """
            
            class Test {
            }
        """,
        after = """
            class Test {
            }
        """,
        afterConditions = { cu -> assertThat(cu.classes[0].prefix.whitespace).isEmpty() }
    )

    @Test
    fun minimumAroundClass(jp: JavaParser.Builder<*, *>) = assertChanged(
        jp.styles(blankLines {
            withMinimum(minimum.withAroundClass(2))
        }).build(),
        before = """
            import java.util.Vector;

            public class Test {
            }

            class Test2 {
            }
        """,
        after = """
            import java.util.Vector;

            public class Test {
            }


            class Test2 {
            }
        """.trimIndent()
    )

    @Test
    fun minimumAfterClassHeader(jp: JavaParser.Builder<*, *>) = assertChanged(
        jp.styles(blankLines {
            withMinimum(minimum.withAfterClassHeader(1))
        }).build(),
        before = """
            public class Test {
                private int field1;
            }
        """,
        after = """
            public class Test {

                private int field1;
            }
        """
    )

    @Test
    fun minimumBeforeClassEnd(jp: JavaParser.Builder<*, *>) = assertChanged(
        jp.styles(blankLines {
            withMinimum(minimum.withBeforeClassEnd(1))
        }).build(),
        before = """
            public class Test {
            }
        """,
        after = """
            public class Test {

            }
        """
    )

    @Test
    fun minimumAfterAnonymousClassHeader(jp: JavaParser.Builder<*, *>) = assertChanged(
        jp.styles(blankLines {
            withMinimum(minimum.withAfterAnonymousClassHeader(1))
        }).build(),
        before = """
            public class Test {
                public void test1() {
                    new Runnable() {
                        public void run() {
                        }
                    };
                }
            }
        """,
        after = """
            public class Test {
                public void test1() {
                    new Runnable() {

                        public void run() {
                        }
                    };
                }
            }
        """
    )

    @Test
    fun minimumAroundFieldInInterface(jp: JavaParser.Builder<*, *>) = assertChanged(
        jp.styles(blankLines {
            withMinimum(minimum.withAroundFieldInInterface(1))
        }).build(),
        before = """
            interface TestInterface {
                int MAX = 10;
                int MIN = 1;
            }
        """,
        after = """
            interface TestInterface {
                int MAX = 10;

                int MIN = 1;
            }
        """
    )

    @Test
    fun minimumAroundField(jp: JavaParser.Builder<*, *>) = assertChanged(
        jp.styles(blankLines {
            withMinimum(minimum.withAroundField(1))
        }).build(),
        before = """
            class Test {
                int max = 10;
                int min = 1;
            }
        """,
        after = """
            class Test {
                int max = 10;

                int min = 1;
            }
        """
    )

    @Test
    fun minimumAroundMethodInInterface(jp: JavaParser.Builder<*, *>) = assertChanged(
        jp.styles(blankLines {
            withMinimum(minimum.withAroundMethodInInterface(1))
        }).build(),
        before = """
            interface TestInterface {
                void method1();
                void method2();
            }
        """,
        after = """
            interface TestInterface {
                void method1();

                void method2();
            }
        """
    )

    @Test
    fun minimumAroundMethod(jp: JavaParser.Builder<*, *>) = assertChanged(
        jp.styles(blankLines {
            withMinimum(minimum.withAroundMethod(1))
        }).build(),
        before = """
            class Test {
                void method1() {}
                void method2() {}
            }
        """,
        after = """
            class Test {
                void method1() {}

                void method2() {}
            }
        """
    )

    @Test
    fun beforeMethodBody(jp: JavaParser.Builder<*, *>) = assertChanged(
        jp.styles(blankLines {
            withMinimum(minimum.withBeforeMethodBody(1))
        }).build(),
        before = """
            class Test {
                void method1() {}

                void method2() {
                    int n = 0;
                }
            }
        """,
        after = """
            class Test {
                void method1() {

                }

                void method2() {

                    int n = 0;
                }
            }
        """
    )

    @Test
    fun aroundInitializer(jp: JavaParser.Builder<*, *>) = assertChanged(
        jp.styles(blankLines {
            withMinimum(minimum.withAroundInitializer(1))
        }).build(),
        before = """
            public class Test {
                private int field1;
                {
                    field1 = 2;
                }
                private int field2;
            }
        """,
        after = """
            public class Test {
                private int field1;

                {
                    field1 = 2;
                }

                private int field2;
            }
        """
    )

    @Test
    fun unchanged(jp: JavaParser.Builder<*, *>) = assertUnchanged(
        jp.styles(blankLines()).build(),
        before = """
            package com.intellij.samples;

            public class Test {
            }
        """
    )
}
