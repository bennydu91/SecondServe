plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.secondserve.data"
    compileSdk = 35

    defaultConfig {
        minSdk = 33 // must match :wear (Wear OS 4 = API 33) since DataLayerClient lives here
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.coroutines.android)
    implementation(libs.coroutines.play.services)
    implementation(libs.wearable)

    // HTTP & JSON
    implementation(libs.retrofit)
    implementation(libs.retrofit.moshi)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)

    // WorkManager + Hilt Work
    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // Security
    implementation(libs.security.crypto)

    // Tests
    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.junit.platform.launcher)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(kotlin("test"))
    testImplementation(libs.turbine)
    testImplementation(libs.work.testing)
    implementation(libs.timber)
}
