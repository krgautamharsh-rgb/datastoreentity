plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.kotlin.serialization) // serialization for type safe navigation
    alias(libs.plugins.hilt)             // dagger
    alias(libs.plugins.ksp)              // annotation processing (KSP)
}

android {
    namespace = "com.example.datastoreentity"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.datastoreentity"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)  // compose navigation
    implementation(libs.androidx.hilt.navigation.compose)     // use hilt in compose
    implementation(libs.kotlinx.serialization.json)   // serialization for type safe navigation
    implementation(libs.coil)                         // coil for image loading
    implementation(libs.viewModel.compose)            // viewmodel in compose
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.room.runtime)            // room db
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.dagger.hilt.android)           // dagger hilt
    ksp(libs.dagger.hilt.compiler)                    // hilt annotation processing
    implementation(libs.paging.runtime)                // paging
    implementation(libs.paging.compose)
    implementation(libs.room.paging)
    implementation(libs.lottie.compose)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.okhttp)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test) // implementation only added for testing , other modules will not use this dependency
    api(libs.retrofit)                               // using api this dependency is exposed to other modules as well
    implementation(libs.ksp.symbol.processing.api)     // KSP API for writing custom annotation processors
    api(libs.converter.gson)

    implementation(libs.datastore.preferences)       // datastore
    implementation(libs.work.manager)
    implementation(libs.work.manager.dagger)
    ksp(libs.work.manager.dagger.kapt)
    ksp(project(":processor"))
}