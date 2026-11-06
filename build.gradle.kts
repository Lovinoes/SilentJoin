plugins {
    java
}

group = "de.lovinoes"
version = "1.0.0"

repositories {
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }
}

val velocity: SourceSet = sourceSets.create("velocity")

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.+")
    "velocityCompileOnly"("com.velocitypowered:velocity-api:4.2.0")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<ProcessResources> {
    val pluginVersion = version.toString()
    inputs.property("version", pluginVersion)
    filesMatching(listOf("paper-plugin.yml", "velocity-plugin.json")) {
        expand("version" to pluginVersion)
    }
}

tasks.jar {
    from(velocity.output)
}
