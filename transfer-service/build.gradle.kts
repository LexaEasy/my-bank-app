plugins {
    alias(libs.plugins.spring.cloud.contract)
}

contracts {
    baseClassForTests.set("ru.practicum.bank.transfer.contract.TransferContractBase")
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.oauth2.client)
    implementation(libs.spring.boot.starter.oauth2.resource.server)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.kafka)

    "contractTestImplementation"(libs.spring.boot.starter.test)
    "contractTestImplementation"(libs.spring.cloud.starter.contract.verifier)
    "contractTestImplementation"(libs.spring.security.test)
    testImplementation(libs.spring.kafka.test)
    testImplementation(libs.spring.security.test)
}
