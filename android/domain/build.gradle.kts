plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(libs.coroutines.core)
    // JSR-330 annotations (@Inject) pour les use cases injectables via Hilt
    compileOnly("javax.inject:javax.inject:1")

    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
