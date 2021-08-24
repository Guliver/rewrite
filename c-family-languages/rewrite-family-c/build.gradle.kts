// run manually with -x compileKotlin when you need to regenerate
tasks.register<JavaExec>("generateAntlrSources") {
    main = "org.antlr.v4.Tool"

    args = listOf(
        "-o", "src/main/java/org/openrewrite/family/c/internal/grammar",
        "-package", "org.openrewrite.family.c.internal.grammar",
        "-visitor"
    ) + fileTree("src/main/antlr").matching { include("**/*.g4") }.map { it.path }

    classpath = sourceSets["main"].runtimeClasspath
}

dependencies {
    api(project(":rewrite-core"))
    api("org.jetbrains:annotations:latest.release")
    api("com.fasterxml.jackson.core:jackson-annotations:2.12.+")

    implementation("org.antlr:antlr4:latest.release")
    implementation("commons-lang:commons-lang:latest.release")
}