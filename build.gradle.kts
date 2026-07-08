import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension
import org.springframework.boot.gradle.tasks.bundling.BootJar
import org.gradle.language.jvm.tasks.ProcessResources
import java.time.Duration

plugins {
    java
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.cloud.contract) apply false
    alias(libs.plugins.spring.dependency.management) apply false
}

group = "ru.practicum.bank"
version = "0.0.1-SNAPSHOT"

val springCloudVersion = libs.versions.spring.cloud.get()
val springBootStarterTest = libs.spring.boot.starter.test

subprojects {
    apply(plugin = "java")
    apply(plugin = "org.springframework.boot")
    apply(plugin = "io.spring.dependency-management")

    group = rootProject.group
    version = rootProject.version

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        systemProperty("spring.profiles.active", "test")
        timeout.set(Duration.ofMinutes(5))
    }

    if (name != "shared") {
        tasks.named<ProcessResources>("processResources") {
            from(rootProject.file("config/logback-spring.xml"))
        }
    }

    tasks.named<BootJar>("bootJar") {
        archiveFileName.set("${project.name}.jar")
    }

    tasks.named<Jar>("jar") {
        enabled = false
    }

    extensions.configure<DependencyManagementExtension> {
        imports {
            mavenBom("org.springframework.cloud:spring-cloud-dependencies:$springCloudVersion")
        }
    }

    dependencies {
        "testImplementation"(springBootStarterTest)
    }
}
