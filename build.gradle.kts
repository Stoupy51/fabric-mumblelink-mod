@file:Suppress("PropertyName")

import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    idea
    `maven-publish`
    id("fabric-loom") version "1.14.0+"
    id("com.github.ben-manes.versions") version "0.42.0"
    id("com.modrinth.minotaur") version "2.+"
    id("com.diffplug.spotless") version "7.+"
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.serialization") version "2.2.21"
    id("org.shipkit.shipkit-auto-version") version "1.+"
    id("org.shipkit.shipkit-changelog") version "1.+"
    id("org.shipkit.shipkit-github-release") version "1.+"
}

tasks.withType<DependencyUpdatesTask> {
    gradleReleaseChannel = "current"
    rejectVersionIf {
        candidate.version.contains("""-M\d+""".toRegex()) ||
            candidate.version.contains("RC")
    }
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.fabricmc.net/")
        name = "Fabric"
    }
    maven {
        url = uri("https://maven.shedaniel.me/")
        name = "shedaniel"
    }
    maven {
        url = uri("https://maven.terraformersmc.com/releases/")
        name = "TerraformersMC"
    }
    maven {
        url = uri("https://maven.skaggsm.com/releases")
        name = "Personal"
    }
}

val modrinth_id: String by project
val archives_base_name: String by project
val maven_group: String by project
val minecraft_version: String by project
val yarn_mappings: String by project
val loader_version: String by project
val fabric_version: String by project
val kotlin_version: String by project
val cloth_config_version: String by project
val fiber_2_cloth_version: String by project
val fiber_version: String by project
val modmenu_version: String by project

base {
    archivesName.set(archives_base_name)
    group = maven_group
}

loom {
}

dependencies {
    // to change the versions see the gradle.properties file
    minecraft("com.mojang:minecraft:$minecraft_version")
    mappings("net.fabricmc:yarn:$yarn_mappings:v2")
    modImplementation("net.fabricmc:fabric-loader:$loader_version")

    // Fabric API. This is technically optional, but you probably want it anyway.
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabric_version")

    modImplementation("net.fabricmc:fabric-language-kotlin:$kotlin_version")
    include("net.fabricmc:fabric-language-kotlin:$kotlin_version")

    modImplementation("com.terraformersmc:modmenu:$modmenu_version")

    modImplementation("me.shedaniel.cloth:cloth-config-fabric:$cloth_config_version")
    include("me.shedaniel.cloth:cloth-config-fabric:$cloth_config_version")

    modImplementation("me.shedaniel.cloth:fiber2cloth:$fiber_2_cloth_version")
    include("me.shedaniel.cloth:fiber2cloth:$fiber_2_cloth_version")

    modImplementation("me.zeroeightsix:fiber:$fiber_version")
    include("me.zeroeightsix:fiber:$fiber_version")

    include("com.skaggsm:java-mumble-link:0.2.6")
    implementation("com.skaggsm:java-mumble-link:0.2.6")
    include("com.skaggsm:jvm-shared-memory:0.2.9")
    implementation("com.skaggsm:jvm-shared-memory:0.2.9")
}

tasks.processResources {
    inputs.properties(
        "version" to project.version,
    )

    filesMatching("fabric.mod.json") {
        expand(
            "version" to project.version,
        )
    }
}

tasks.withType<JavaCompile> {
    // ensure that the encoding is set to UTF-8, no matter what the system default is
    // this fixes some edge cases with special characters not displaying correctly
    // see http://yodaconditions.net/blog/fix-for-java-file-encoding-problems-with-gradle.html
    // If Javadoc is generated, this must be specified in that task too.
    options.encoding = "UTF-8"
    // Minecraft 1.21 (21w19a) upwards uses Java 21.
    options.release.set(21)
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    // Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task
    // if it is present.
    // If you remove this line, sources will not be generated.
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("mod") {
            from(components["java"])
        }
    }

    repositories {
        mavenLocal()
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/magneticflux-/fabric-mumblelink-mod")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

tasks.generateChangelog {
    repository = "magneticflux-/fabric-mumblelink-mod"
    previousRevision = project.ext["shipkit-auto-version.previous-tag"]?.toString()
    githubToken = System.getenv("GITHUB_TOKEN")
}

tasks.githubRelease {
    dependsOn(tasks.generateChangelog)
    repository = "magneticflux-/fabric-mumblelink-mod"
    changelog = tasks.generateChangelog.get().outputFile
    githubToken = System.getenv("GITHUB_TOKEN")
    newTagRevision = System.getenv("GITHUB_SHA")
}

modrinth {
    // Stored in ~/.gradle/gradle.properties
    when {
        project.hasProperty("modrinthApiKey") -> token.set(project.property("modrinthApiKey").toString())
        System.getenv("MODRINTH_API_KEY") != null -> token.set(System.getenv("MODRINTH_API_KEY"))
        else -> println("No Modrinth API key found, \'modrinth\' tasks will not work")
    }
    projectId.set(modrinth_id)
    versionNumber.set(version.toString())
    gameVersions.add(minecraft_version)
    uploadFile.set(tasks.remapJar as Any)
    additionalFiles.add(tasks.remapSourcesJar as Any)
    loaders.addAll("fabric", "quilt")
    dependencies {
        required.project("fabric-api")
        optional.project("modmenu")
        embedded.project("cloth-config")
        embedded.project("fabric-language-kotlin")
    }
}

spotless {
    kotlin {
        ktlint("1.8.+")
    }
    kotlinGradle {
        ktlint("1.8.+")
    }
}

afterEvaluate {
    val modrinthTask = tasks.modrinth
    tasks.publish {
        dependsOn(modrinthTask)
    }
}
