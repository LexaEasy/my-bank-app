dependencies {
    implementation(libs.logstash.logback.encoder)
    implementation(libs.micrometer.tracing.bridge.brave)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.oauth2.resource.server)
    implementation(libs.spring.cloud.starter.gateway.server.webflux)
    implementation(libs.zipkin.reporter.brave)

    runtimeOnly(libs.micrometer.registry.prometheus)
}
