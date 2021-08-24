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
package org.openrewrite.family.c.tree;

import org.openrewrite.internal.lang.Nullable;

/**
 * A tree with type attribution information. Unlike {@link TypeTree},
 * this does not necessarily mean the tree is the name of a type. So for
 * example, a {@link MethodInvocation} is a {@link TypedTree} but
 * not a {@link TypeTree}.
 */
public interface TypedTree extends C {
    @Nullable
    CType getType();

    <T extends C> T withType(@Nullable CType type);
}