/*
 * Copyright 2021 the original author or authors.
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
package org.openrewrite.hcl.marker;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.openrewrite.Recipe;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.marker.RecipeSearchResult;

import java.util.UUID;

import static org.openrewrite.Tree.randomId;

public class HclSearchResult extends RecipeSearchResult {
    @JsonCreator
    public HclSearchResult(UUID id, Recipe recipe, @Nullable String description) {
        super(id, recipe, description);
    }

    public HclSearchResult(UUID id, Recipe recipe) {
        super(id, recipe, null);
    }

    public HclSearchResult(Recipe recipe) {
        super(randomId(), recipe, null);
    }

    public HclSearchResult(Recipe recipe, @Nullable String description) {
        super(randomId(), recipe, description);
    }
}
