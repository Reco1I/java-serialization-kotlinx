import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Reads Java's object serialization without the JVM: JavaObjectInputStream, and JavaSerialization,
// a decode-only kotlinx.serialization format over it. Kotlin Multiplatform, android and jvm for
// now, all of it common code (checkCommonSources checks it).
plugins {
    kotlin("multiplatform") version "2.4.10"
    id("com.android.kotlin.multiplatform.library") version "9.2.1"
    // For the tests' @Serializable classes.
    kotlin("plugin.serialization") version "2.4.10"
    `maven-publish`
}

// JitPack serves it as com.github.Reco1I.java-serialization-kotlinx:java-serialization-kotlinx:<tag>
// (the group of its multi-module projects, as it has an artifact per target:
// java-serialization-kotlinx-jvm, -android, which Gradle picks from the root one's metadata).
group = "com.github.Reco1I.java-serialization-kotlinx"
version = "0.1.0"

kotlin {
    android {
        namespace = "com.reco1l.javaserialization"
        compileSdk = 36
        minSdk = 21

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    explicitApi()

    sourceSets {
        commonMain.dependencies {
            api("com.squareup.okio:okio:3.9.1")
            api("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.0")
        }

        jvmTest.dependencies {
            implementation("junit:junit:4.13.2")
        }
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("Java Serialization for kotlinx.serialization")
            description.set(
                "Reads Java's object serialization streams without the JVM, and decodes them into " +
                    "@Serializable classes with a kotlinx.serialization format. Kotlin Multiplatform."
            )
            url.set("https://github.com/Reco1I/java-serialization-kotlinx")
            licenses {
                license {
                    name.set("The Apache License, Version 2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                }
            }
            developers {
                developer {
                    id.set("Reco1I")
                    name.set("Reco1l")
                }
            }
            scm {
                url.set("https://github.com/Reco1I/java-serialization-kotlinx")
                connection.set("scm:git:https://github.com/Reco1I/java-serialization-kotlinx.git")
            }
        }
    }

    // ./gradlew publishAllPublicationsToBuildRepository, to look at what's published.
    repositories {
        maven {
            name = "build"
            url = uri(layout.buildDirectory.dir("repository"))
        }
    }
}


/**
 * Fails when common code uses the JVM. Both targets are JVMs, so the compiler lets commonMain see
 * the JDK; this catches what it doesn't: Java imports and the stdlib members only the JVM has.
 * Comments are skipped, and so are what strings hold (such as the names of the Java classes the
 * format reads).
 */
abstract class CheckCommonSources : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:OutputFile
    abstract val report: RegularFileProperty

    private val forbidden = mapOf(
        Regex("""^\s*import (java|javax)\.""") to "a Java import",
        Regex("""\bjava\.(lang|util|io|text|net|nio|math|time)\.""") to "a Java class",
        Regex("""\bString\.format\b|\.format\(Locale|"\.format\(""") to "String.format",
        Regex("""\bSystem\.[a-zA-Z]""") to "System",
        Regex("""\.javaClass\b|::class\.java\b""") to "a Java class reference, use ::class",
        Regex("""(?<![.\w])Math\.[a-zA-Z]""") to "java.lang.Math, use kotlin.math",
        Regex("""\bkotlin\.jvm\.(Volatile|Synchronized|Throws|Strictfp|Transient)\b|@Synchronized\b""") to "a JVM-only annotation",
    )

    // A string on one line, with its escapes.
    private val stringLiteral = Regex(""""(?:[^"\\]|\\.)*"""")

    @TaskAction
    fun check() {
        val problems = mutableListOf<String>()

        sources.asFileTree.matching { include("**/*.kt") }.forEach { file ->
            var inBlockComment = false
            file.readLines().forEachIndexed { index, raw ->
                var line = raw
                if (inBlockComment) {
                    val end = line.indexOf("*/")
                    if (end < 0) return@forEachIndexed
                    line = line.substring(end + 2)
                    inBlockComment = false
                }
                line = line.replace(stringLiteral, "\"\"")
                val blockStart = line.indexOf("/*")
                if (blockStart >= 0) {
                    inBlockComment = line.indexOf("*/", blockStart + 2) < 0
                    line = line.substring(0, blockStart)
                }
                line = line.substringBefore("//")

                for ((pattern, reason) in forbidden) {
                    if (pattern.containsMatchIn(line)) {
                        problems += "${file.path}:${index + 1}: $reason: ${raw.trim()}"
                    }
                }
            }
        }

        report.get().asFile.writeText(problems.joinToString("\n"))
        if (problems.isNotEmpty()) {
            throw GradleException("commonMain uses the JVM:\n" + problems.joinToString("\n"))
        }
    }
}

val checkCommonSources by tasks.registering(CheckCommonSources::class) {
    sources.from(layout.projectDirectory.dir("src/commonMain/kotlin"))
    report.set(layout.buildDirectory.file("reports/checkCommonSources.txt"))
}

tasks.matching { it.name == "check" || it.name == "jvmTest" }.configureEach {
    dependsOn(checkCommonSources)
}
