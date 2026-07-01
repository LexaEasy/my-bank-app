dependencies {
    implementation(project(":shared"))
    implementation(libs.spring.boot.starter.json)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.kafka)

    testImplementation(libs.spring.kafka.test)
}
