plugins {
    alias(libs.plugins.spring.cloud.contract)
}

contracts {
    baseClassForTests.set(
        "ru.practicum.bank.notifications.contract.NotificationsMessagingContractBase",
    )
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.json)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.kafka)

    "contractTestImplementation"(libs.spring.boot.starter.test)
    "contractTestImplementation"(libs.spring.cloud.starter.contract.verifier)
    "contractTestImplementation"(libs.spring.integration.core)
    testImplementation(libs.spring.kafka.test)
}
