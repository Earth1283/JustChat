plugins {
    kotlin("jvm") version "2.5.0-Beta1"
    id("com.gradleup.shadow") version "9.6.1"
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://repo.extendedclip.com/releases/") {
        content { includeGroup("me.clip") }
    }
    maven("https://jitpack.io") {
        content { includeGroup("com.github.MilkBowl") }
    }
}

val adventureVersion = "5.2.0"

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.20.6-R0.1-SNAPSHOT")
    compileOnly("net.luckperms:api:5.5")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1") { isTransitive = false }
    compileOnly("me.clip:placeholderapi:2.12.3") { isTransitive = false }

    implementation("net.kyori:adventure-api:$adventureVersion")
    implementation("net.kyori:adventure-text-minimessage:$adventureVersion")
    implementation("net.kyori:adventure-text-serializer-gson:$adventureVersion")
    implementation("net.kyori:adventure-text-serializer-legacy:$adventureVersion")
    implementation("net.kyori:adventure-text-serializer-plain:$adventureVersion")
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("org.xerial:sqlite-jdbc:3.53.4.0") {
        exclude(group = "org.slf4j")
    }

    testImplementation(kotlin("test"))
    testImplementation("org.spigotmc:spigot-api:1.20.6-R0.1-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(21)
}

tasks {
    register<JavaExec>("benchmark") {
        group = "verification"
        description = "Micro-benchmark of the chat render path"
        classpath = sourceSets["test"].runtimeClasspath
        mainClass.set("io.github.Earth1283.justChat.bench.RenderBenchmarkKt")
    }

    test {
        useJUnitPlatform()
        testLogging {
            events("failed", "skipped")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }

    shadowJar {
        archiveClassifier.set("")
        mergeServiceFiles()

        val libs = "io.github.Earth1283.justChat.libs"
        relocate("net.kyori", "$libs.kyori")
        relocate("com.google.gson", "$libs.gson")
        relocate("kotlin", "$libs.kotlin")
        relocate("org.jetbrains.annotations", "$libs.jetbrains.annotations")
        relocate("org.intellij.lang.annotations", "$libs.intellij.annotations")

        exclude("org/sqlite/native/FreeBSD/**")
        exclude("org/sqlite/native/Linux-Android/**")
        exclude("org/sqlite/native/Linux-Musl/**")
        exclude("org/sqlite/native/Linux/arm/**")
        exclude("org/sqlite/native/Linux/armv6/**")
        exclude("org/sqlite/native/Linux/armv7/**")
        exclude("org/sqlite/native/Linux/ppc64/**")
        exclude("org/sqlite/native/Linux/riscv64/**")
        exclude("org/sqlite/native/Linux/x86/**")
        exclude("org/sqlite/native/Windows/x86/**")
        exclude("org/sqlite/native/Windows/armv7/**")
        exclude("META-INF/versions/*/module-info.class")
        exclude("module-info.class")
        exclude("META-INF/maven/**")
        exclude("META-INF/*.kotlin_module")
        exclude("DebugProbesKt.bin")
    }

    jar {
        archiveClassifier.set("plain")
    }

    build {
        dependsOn(shadowJar)
    }

    runServer {
        minecraftVersion("1.20.6")
        jvmArgs("-Xms2G", "-Xmx2G", "-Dcom.mojang.eula.agree=true")
    }

    processResources {
        val props = mapOf("version" to version)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
