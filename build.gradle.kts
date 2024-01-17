plugins {
    id("fabric-loom")
    kotlin("jvm").version(System.getProperty("kotlin_version"))
    id("org.jlleitschuh.gradle.ktlint") version "12.0.3"
}
base {
    archivesName.set(project.extra["archives_base_name"] as String)
}
version = project.extra["mod_version"] as String
group = project.extra["maven_group"] as String
repositories {
    maven("https://cursemaven.com/")
    exclusiveContent {
        forRepository {
            maven {
                name = "Modrinth"
                url = uri("https://api.modrinth.com/maven")
            }
        }
        filter {
            includeGroup("maven.modrinth")
        }
    }
    maven("https://nexus.twelveiterations.com/repository/maven-public/")
    maven("https://maven.shedaniel.me/")
}
dependencies {
    minecraft("com.mojang", "minecraft", project.extra["minecraft_version"] as String)
    // Official mappings required for net.blay09.mods.waystones-common API
    mappings(loom.officialMojangMappings())

    modImplementation("net.fabricmc", "fabric-loader", project.extra["loader_version"] as String)
    modImplementation("net.fabricmc.fabric-api", "fabric-api", project.extra["fabric_version"] as String)
    modImplementation("net.fabricmc", "fabric-language-kotlin", project.extra["fabric_language_kotlin_version"] as String)

    // Allow xaerominimap or xaerominimapfairplay (same API)
    modApi("curse.maven", "xaero-minimap-263420", project.extra["xaero_minimap_id"] as String)
    modRuntimeOnly("curse.maven", "xaero-minimap-263420", project.extra["xaero_minimap_id"] as String)
    modApi("curse.maven", "xaero-fairplay-263466", project.extra["xaero_fairplay_id"] as String)
    modRuntimeOnly("curse.maven", "xaero-fairplay-263466", project.extra["xaero_fairplay_id"] as String)

    // Support either fabric waystones mods (or both!)
    modApi("net.blay09.mods", "balm-common", project.extra["balm_version"] as String)
    modRuntimeOnly("net.blay09.mods", "balm-common", project.extra["balm_version"] as String)
    modApi("net.blay09.mods", "waystones-common", project.extra["waystones_version"] as String)
    modRuntimeOnly("net.blay09.mods", "waystones-common", project.extra["waystones_version"] as String)

    // Not availale for 1.20.4 yet
    modApi("maven.modrinth", "fwaystones", project.extra["fwaystones_version"] as String)
    modRuntimeOnly("maven.modrinth", "fwaystones", project.extra["fwaystones_version"] as String)
}
tasks {
    val javaVersion = JavaVersion.toVersion((project.extra["java_version"] as String).toInt())
    withType<JavaCompile> {
        options.encoding = "UTF-8"
        sourceCompatibility = javaVersion.toString()
        targetCompatibility = javaVersion.toString()
        options.release.set(javaVersion.toString().toInt())
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> { kotlinOptions { jvmTarget = javaVersion.toString() } }
    jar { from("LICENSE") { rename { "${it}_${base.archivesName.get()}" } } }
    processResources {
        filesMatching("fabric.mod.json") {
            expand(
                mutableMapOf(
                    "version" to project.extra["mod_version"] as String,
                    "fabricloader" to project.extra["loader_version"] as String,
                    "fabric_api" to project.extra["fabric_version"] as String,
                    "fabric_language_kotlin" to project.extra["fabric_language_kotlin_version"] as String,
                    "minecraft" to project.extra["minecraft_version"] as String,
                    "java" to project.extra["java_version"] as String,
                    "xaero_minimap" to project.extra["xaero_minimap_version"] as String,
                    "xaero_fairplay" to project.extra["xaero_fairplay_version"] as String,
                    "waystones" to project.extra["waystones_version"] as String,
                    "fwaystones" to project.extra["fwaystones_version"] as String,
                ),
            )
        }
        filesMatching("*.mixins.json") { expand(mutableMapOf("java" to project.extra["java_version"] as String)) }
    }
    java {
        toolchain { languageVersion.set(JavaLanguageVersion.of(javaVersion.toString())) }
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
        withSourcesJar()
    }
}
