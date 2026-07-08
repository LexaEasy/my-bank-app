dependencies {
    implementation(project(":shared"))
    implementation(libs.logstash.logback.encoder)
    implementation(libs.micrometer.tracing.bridge.brave)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.aop)
    implementation(libs.spring.boot.starter.oauth2.client)
    implementation(libs.spring.boot.starter.thymeleaf)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.zipkin.reporter.brave)

    runtimeOnly(libs.micrometer.registry.prometheus)
    testImplementation(libs.spring.security.test)
}
