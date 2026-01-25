plugins {
    java
    application
    id("xyz.jpenilla.run-paper") version "2.3.1"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

application.mainClass = "top.modpotato.Main"

group = project.property("group").toString()
version = project.property("version").toString()
description = project.property("description").toString()

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven {
        name = "tcoded-releases"
        url = uri("https://repo.tcoded.com/releases")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:${project.property("paperVersion")}")
    implementation("com.tcoded:FoliaLib:0.5.1")
    compileOnly("net.kyori:adventure-api:${project.property("adventureVersion")}")
}

tasks {
    jar {
        from("LICENSE") {
            rename { "${it}_${project.name}" }
        }
    }
    
    compileJava {
        options.encoding = Charsets.UTF_8.name()
        options.release.set(17)
    }
    
    javadoc {
        options.encoding = Charsets.UTF_8.name()
    }
    
    processResources {
        filteringCharset = Charsets.UTF_8.name()
        filesMatching("plugin.yml") {
            expand(
                "version" to project.version,
                "name" to project.name,
                "description" to project.description,
                "author" to project.property("author")
            )
        }
    }
    
    runServer {
        minecraftVersion("1.20")
    }
    
    shadowJar {
        relocate("com.tcoded.folialib", "top.modpotato.antinetherite.lib.folialib")
        archiveClassifier.set("")
    }
    
    build {
        dependsOn(shadowJar)
    }

    // Disable application distribution tasks as they are not needed for a plugin
    // and cause implicit dependency issues with shadowJar replacing the main jar
    distZip { enabled = false }
    distTar { enabled = false }
    startScripts { enabled = false }
    startShadowScripts { enabled = false }
    shadowDistTar { enabled = false }
    shadowDistZip { enabled = false }
}