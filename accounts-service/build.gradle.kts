dependencies {
    implementation(libs.flyway.core)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.oauth2.resource.server)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.web)

    runtimeOnly(libs.flyway.database.postgresql)
    runtimeOnly(libs.postgresql)
    testRuntimeOnly(libs.h2)
}
