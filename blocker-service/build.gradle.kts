plugins {
    alias(libs.plugins.spring.cloud.contract)
}

contracts {
    baseClassForTests.set("ru.practicum.bank.blocker.contract.BlockerContractBase")
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.logstash.logback.encoder)
    implementation(libs.micrometer.tracing.bridge.brave)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.oauth2.resource.server)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.zipkin.reporter.brave)

    runtimeOnly(libs.micrometer.registry.prometheus)
    "contractTestImplementation"(libs.spring.boot.starter.test)
    "contractTestImplementation"(libs.spring.cloud.starter.contract.verifier)
    testImplementation(libs.spring.security.test)
}
