dependencies {
    implementation(libs.flyway.core)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.oauth2.resource.server)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.cloud.starter.netflix.eureka.client)

    runtimeOnly(libs.flyway.database.postgresql)
    runtimeOnly(libs.postgresql)
    testImplementation(libs.spring.security.test)
    testRuntimeOnly(libs.h2)
}
