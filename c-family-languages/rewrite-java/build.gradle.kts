// run manually with -x compileKotlin when you need to regenerate
tasks.register<JavaExec>("generateAntlrSources") {
    main = "org.antlr.v4.Tool"

    args = listOf(
            "-o", "src/main/java/org/openrewrite/java/internal/grammar",
            "-package", "org.openrewrite.java.internal.grammar",
            "-visitor"
    ) + fileTree("src/main/antlr").matching { include("**/*.g4") }.map { it.path }

    classpath = sourceSets["main"].runtimeClasspath
}

dependencies {
    api(project(":rewrite-core"))
    api(project(":rewrite-family-c"))

    api("io.micrometer:micrometer-core:latest.release")
    api("org.jetbrains:annotations:latest.release")

    implementation("org.antlr:antlr4:latest.release")
    compileOnly("com.puppycrawl.tools:checkstyle:latest.release") {
        isTransitive = false
    }

    implementation("io.github.classgraph:classgraph:latest.release")

    api("com.fasterxml.jackson.core:jackson-annotations:2.12.+")

    implementation("org.ow2.asm:asm:latest.release")
    implementation("org.ow2.asm:asm-util:latest.release")

    testImplementation("org.yaml:snakeyaml:latest.release")
    testImplementation("com.puppycrawl.tools:checkstyle:latest.release") {
        isTransitive = false
    }
}

tasks.withType<Javadoc> {
    // generated ANTLR sources violate doclint
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")

    // ChangePackage and OrderImports due to lombok error which looks similar to this:
    //     openrewrite/rewrite/rewrite-java/src/main/java/org/openrewrite/java/OrderImports.java:42: error: cannot find symbol
    // @AllArgsConstructor(onConstructor_=@JsonCreator)
    //                     ^
    //   symbol:   method onConstructor_()
    //   location: @interface AllArgsConstructor
    // 1 error
    exclude("**/JavaParser**", "**/ChangePackage**", "**/OrderImports**")
}
