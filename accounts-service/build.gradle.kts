dependencies {
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.web)

    runtimeOnly(libs.postgresql)
    testRuntimeOnly(libs.h2)
}
