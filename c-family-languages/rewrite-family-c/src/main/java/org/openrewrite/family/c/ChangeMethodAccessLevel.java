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
package org.openrewrite.family.c;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.Validated;
import org.openrewrite.family.c.search.DeclaresMethod;
import org.openrewrite.family.c.tree.C;

@Value
@EqualsAndHashCode(callSuper = true)
public class ChangeMethodAccessLevel extends Recipe {
    @Option(displayName = "Method pattern",
            description = "A method pattern, expressed as a pointcut expression, that is used to find matching method declarations/invocations.",
            example = "org.mockito.Matchers anyVararg()")
    String methodPattern;

    @Option(displayName = "New access level",
            description = "New method access level to apply to the method.",
            example = "public",
            valid = {"private", "protected", "package", "public"})
    String newAccessLevel;

    @Override
    public String getDisplayName() {
        return "Change method access level";
    }

    @Override
    public String getDescription() {
        return "Change the access level (public, protected, private, package private) of a method.";
    }

    @Override
    public Validated validate() {
        return super.validate().and(Validated.test("newAccessLevel", "Must be one of 'private', 'protected', 'package', 'public'",
                newAccessLevel, level -> level.equals("private") || level.equals("protected") || level.equals("package") || level.equals("public")));
    }

    @Override
    protected CVisitor<ExecutionContext> getSingleSourceApplicableTest() {
        return new DeclaresMethod<>(methodPattern);
    }

    @Override
    public CVisitor<ExecutionContext> getVisitor() {
        C.Modifier.Type type;
        switch(newAccessLevel) {
            case "public":
                type = C.Modifier.Type.Public;
                break;
            case "protected":
                type = C.Modifier.Type.Protected;
                break;
            case "private":
                type = C.Modifier.Type.Private;
                break;
            default:
                type = null;
        }

        return new ChangeMethodAccessLevelVisitor<>(new MethodMatcher(methodPattern), type);
    }
}
