plugins {
    alias(libs.plugins.spring.cloud.contract)
}

contracts {
    baseClassForTests.set("ru.practicum.bank.accounts.contract.AccountsContractBase")
}

dependencies {
    implementation(libs.flyway.core)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.aop)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.oauth2.resource.server)
    implementation(libs.spring.retry)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.cloud.starter.config)
    implementation(libs.spring.cloud.starter.netflix.eureka.client)

    runtimeOnly(libs.flyway.database.postgresql)
    runtimeOnly(libs.postgresql)
    "contractTestImplementation"(libs.spring.boot.starter.test)
    "contractTestImplementation"(libs.spring.cloud.starter.contract.verifier)
    "contractTestImplementation"(libs.spring.security.test)
    testImplementation(libs.spring.security.test)
    testRuntimeOnly(libs.h2)
}
