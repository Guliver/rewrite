/*
 * Copyright 2022 the original author or authors.
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
@file:Suppress("StatementWithEmptyBody", "PointlessBooleanExpression")

package org.openrewrite.java.cleanup

import org.junit.jupiter.api.Test
import org.openrewrite.Recipe
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest

@Suppress("ConstantConditions")
interface SimplifyConstantIfBranchExecutionTest : JavaRecipeTest {
    override val recipe: Recipe
        get() = SimplifyConstantIfBranchExecution()

    @Suppress("ConstantConditions")
    @Test
    fun doNotChangeNonIf(jp: JavaParser) = assertUnchanged(
        jp,
        before = """
            public class A {
                public void test() {
                    boolean b = true;
                    if (!b) {
                        System.out.println("hello");
                    }
                }
            }
        """
    )

    @Test
    fun simplifyConstantIfTrue(jp: JavaParser) = assertChanged(
        jp,
        before = """
            public class A {
                public void test() {
                    if (true) {
                        System.out.println("hello");
                    }
                }
            }
        """,
        after = """
            public class A {
                public void test() {
                    System.out.println("hello");
                }
            }
        """
    )

    @Test
    fun simplifyConstantIfTrueInParens(jp: JavaParser) = assertChanged(
        jp,
        before = """
            public class A {
                public void test() {
                    if ((true)) {
                        System.out.println("hello");
                    }
                }
            }
        """,
        after = """
            public class A {
                public void test() {
                    System.out.println("hello");
                }
            }
        """
    )

    @Test
    fun simplifyConstantIfNotFalse(jp: JavaParser) = assertChanged(
        jp,
        before = """
            public class A {
                public void test() {
                    if (!false) {
                        System.out.println("hello");
                    }
                }
            }
        """,
        after = """
            public class A {
                public void test() {
                    System.out.println("hello");
                }
            }
        """
    )

    @Test
    @Suppress("DuplicateCondition")
    fun simplifyConstantIfTrueOrTrue(jp: JavaParser) = assertChanged(
        jp,
        before = """
            public class A {
                public void test() {
                    if (true || true) {
                        System.out.println("hello");
                    }
                }
            }
        """,
        after = """
            public class A {
                public void test() {
                    System.out.println("hello");
                }
            }
        """
    )

    @Test
    fun simplifyConstantIfFalse(jp: JavaParser) = assertChanged(
        jp,
        before = """
            public class A {
                public void test() {
                    if (false) {
                        System.out.println("hello");
                    }
                }
            }
        """,
        after = """
            public class A {
                public void test() {
                }
            }
        """
    )

    @Test
    fun simplifyConstantIfTrueElse(jp: JavaParser) = assertChanged(
        jp,
        before = """
            public class A {
                public void test() {
                    if (true) {
                    } else {
                        System.out.println("hello");
                    }
                }
            }
        """,
        after = """
            public class A {
                public void test() {
                }
            }
        """
    )

    @Test
    fun simplifyConstantIfFalseElse(jp: JavaParser) = assertChanged(
        jp,
        before = """
            public class A {
                public void test() {
                    if (false) {
                    } else {
                        System.out.println("hello");
                    }
                }
            }
        """,
        after = """
            public class A {
                public void test() {
                    System.out.println("hello");
                }
            }
        """
    )

    @Test
    fun simplifyConstantIfTrueNoBlock(jp: JavaParser) = assertChanged(
        jp,
        before = """
            public class A {
                public void test() {
                    if (true) System.out.println("hello");
                }
            }
        """,
        after = """
            public class A {
                public void test() {
                    System.out.println("hello");
                }
            }
        """
    )

    @Test
    fun simplifyConstantIfFalseNoBlock(jp: JavaParser) = assertChanged(
        jp,
        before = """
            public class A {
                public void test() {
                    if (false) System.out.println("hello");
                }
            }
        """,
        after = """
            public class A {
                public void test() {
                }
            }
        """
    )

    @Test
    fun simplifyConstantIfTrueEmptyBlock(jp: JavaParser) = assertChanged(
        jp,
        before = """
            public class A {
                public void test() {
                    if (true) {}
                }
            }
        """,
        after = """
            public class A {
                public void test() {
                }
            }
        """
    )

    @Test
    fun simplifyConstantIfFalseEmptyBlock(jp: JavaParser) = assertChanged(
        jp,
        before = """
            public class A {
                public void test() {
                    if (false) {}
                }
            }
        """,
        after = """
            public class A {
                public void test() {
                }
            }
        """
    )

    @Test
    fun simplifyConstantIfTrueElseIf(jp: JavaParser) = assertChanged(
        jp,
        before = """
            public class A {
                public void test(boolean a) {
                    if (true) {
                        System.out.println("hello");
                    } else if (a) {
                        System.out.println("goodbye");
                    }
                }
            }
        """,
        after = """
            public class A {
                public void test(boolean a) {
                    System.out.println("hello");
                }
            }
        """
    )

    @Test
    fun simplifyConstantIfFalseElseIf(jp: JavaParser) = assertChanged(
        jp,
        before = """
            public class A {
                public void test(boolean a) {
                    if (false) {
                        System.out.println("hello");
                    } else if (a) {
                        System.out.println("goodbye");
                    }
                }
            }
        """,
        after = """
            public class A {
                public void test(boolean a) {
                    if (a) {
                        System.out.println("goodbye");
                    }
                }
            }
        """
    )

    @Test
    fun simplifyConstantIfTrueElseIfFalse(jp: JavaParser) = assertChanged(
        jp,
        before = """
            public class A {
                public void test() {
                    if (true) {
                    } else if (false) {
                        System.out.println("hello");
                    }
                }
            }
        """,
        after = """
            public class A {
                public void test() {
                }
            }
        """
    )

    @Test
    fun simplifyConstantIfFalseElseIfTrue(jp: JavaParser) = assertChanged(
        jp,
        before = """
            public class A {
                public void test() {
                    if (false) {
                    } else if (true) {
                        System.out.println("hello");
                    }
                }
            }
        """,
        after = """
            public class A {
                public void test() {
                    System.out.println("hello");
                }
            }
        """
    )

    @Test
    fun simplifyConstantIfTrueElseIfFalseNoBlock(jp: JavaParser) = assertChanged(
        jp,
        before = """
            public class A {
                public void test() {
                    if (true) System.out.println("hello");
                    else if (false) System.out.println("goodbye");
                }
            }
        """,
        after = """
            public class A {
                public void test() {
                    System.out.println("hello");
                }
            }
        """
    )

    @Test
    fun simplifyConstantIfFalseElseIfNoBlock(jp: JavaParser) = assertChanged(
        jp,
        before = """
            public class A {
                public void test() {
                    if (false) System.out.println("hello");
                    else if (true) System.out.println("goodbye");
                }
            }
        """,
        after = """
            public class A {
                public void test() {
                    System.out.println("goodbye");
                }
            }
        """
    )

    @Test
    fun simplifyConstantIfTrueElseIfFalseEmptyBlock(jp: JavaParser) = assertChanged(
        jp,
        before = """
            public class A {
                public void test() {
                    if (true) {}
                    else if (false) {}
                }
            }
        """,
        after = """
            public class A {
                public void test() {
                }
            }
        """
    )

    @Test
    fun simplifyConstantIfFalseElseIfTrueEmptyBlock(jp: JavaParser) = assertChanged(
        jp,
        before = """
            public class A {
                public void test() {
                    if (false) {}
                    else if (true) {}
                }
            }
        """,
        after = """
            public class A {
                public void test() {
                }
            }
        """
    )

    @Test
    fun simplifyConstantIfVariableElseIfTrueEmptyBlock(jp: JavaParser) = assertChanged(
        jp,
        before = """
            public class A {
                public void test(boolean a) {
                    if (a) {
                        System.out.println("hello");
                    } else if (true) {
                    }
                }
            }
        """,
        after = """
            public class A {
                public void test(boolean a) {
                    if (a) {
                        System.out.println("hello");
                    }
                }
            }
        """
    )

    @Test
    fun simplifyConstantIfVariableElseIfTruePrint(jp: JavaParser) = assertChanged(
        jp,
        before = """
            public class A {
                public void test(boolean a) {
                    if (a) {
                        System.out.println("hello");
                    } else if (true) {
                        System.out.println("goodbye");
                    }
                }
            }
        """,
        after = """
            public class A {
                public void test(boolean a) {
                    if (a) {
                        System.out.println("hello");
                    } else {
                        System.out.println("goodbye");
                    }
                }
            }
        """
    )

    @Test
    fun simplifyConstantIfVariableElseIfFalseEmptyBlock(jp: JavaParser) = assertChanged(
        jp,
        before = """
            public class A {
                public void test(boolean a) {
                    if (a) {
                        System.out.println("hello");
                    } else if (false) {
                    }
                }
            }
        """,
        after = """
            public class A {
                public void test(boolean a) {
                    if (a) {
                        System.out.println("hello");
                    }
                }
            }
        """
    )

    @Test
    @Suppress("InfiniteLoopStatement")
    fun simplifyConstantIfFalseElseWhileTrueEmptyBlock(jp: JavaParser) = assertChanged(
        jp,
        before = """
            public class A {
                public void test() {
                    if (false) {}
                    else while (true) {
                        System.out.println("hello");
                    }
                }
            }
        """,
        after = """
            public class A {
                public void test() {
                    while (true) {
                        System.out.println("hello");
                    }
                }
            }
        """
    )
}
