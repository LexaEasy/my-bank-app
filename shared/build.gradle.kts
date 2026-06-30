import org.springframework.boot.gradle.tasks.bundling.BootJar

dependencies {
    compileOnly(libs.spring.boot.starter.web)
    compileOnly(libs.spring.boot.starter.validation)
    compileOnly(libs.spring.kafka)

    testImplementation(libs.spring.boot.starter.validation)
    testImplementation(libs.spring.boot.starter.web)
    testImplementation(libs.spring.kafka)
}

tasks.named<BootJar>("bootJar") {
    enabled = false
}

tasks.named<Jar>("jar") {
    enabled = true
}
