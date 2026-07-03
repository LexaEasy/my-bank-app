import org.springframework.boot.gradle.tasks.bundling.BootJar

dependencies {
    implementation(libs.resilience4j.spring.boot3)

    compileOnly(libs.spring.boot.starter.web)
    compileOnly(libs.spring.boot.starter.validation)
    compileOnly(libs.spring.kafka)
    compileOnly(libs.micrometer.core)

    testImplementation(libs.micrometer.core)
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
