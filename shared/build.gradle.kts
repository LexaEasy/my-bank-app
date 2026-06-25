import org.springframework.boot.gradle.tasks.bundling.BootJar

dependencies {
    compileOnly(libs.spring.boot.starter.validation)
}

tasks.named<BootJar>("bootJar") {
    enabled = false
}

tasks.named<Jar>("jar") {
    enabled = true
}
