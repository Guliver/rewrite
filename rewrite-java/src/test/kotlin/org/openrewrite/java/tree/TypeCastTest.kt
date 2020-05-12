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
package org.openrewrite.java.tree

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.openrewrite.java.JavaParser

open class TypeCastTest : JavaParser() {

    @Test
    fun cast() {
        val a = parse("""
            public class A {
                Object o = (Class<String>) Class.forName("java.lang.String");
            }
        """)

        val typeCast = a.classes[0].fields[0].vars[0].initializer as J.TypeCast
        assertEquals("""(Class<String>) Class.forName("java.lang.String")""",
                typeCast.printTrimmed())
    }
}