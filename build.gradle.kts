plugins {
    java
}

group = "ru.practicum.bank"
version = "0.0.1-SNAPSHOT"

subprojects {
    apply(plugin = "java")

    group = rootProject.group
    version = rootProject.version

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
