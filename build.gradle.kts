import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.intellij.platform.grammarkit")
    id("org.jetbrains.changelog")
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        intellijIdea("2025.3.6.1")
        testFramework(TestFrameworkType.Platform)
    }
}

// Lexer (JFlex) and parser/PSI (Grammar-Kit) generation - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-tasks.html#generateLexer
tasks.generateLexer {
    sourceFile = file("src/main/grammar/Lezer.flex")
}

tasks.generateParser {
    sourceFile = file("src/main/grammar/Lezer.bnf")
}

sourceSets.main {
    java.srcDir(tasks.generateLexer)
    java.srcDir(tasks.generateParser)
}

tasks.compileKotlin {
    dependsOn(tasks.generateLexer, tasks.generateParser)
}
