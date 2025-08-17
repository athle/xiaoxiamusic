plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "1.8"
}

android {
    namespace = "com.xiaoxiamusic.media"
    compileSdk = 34

    defaultConfig {
        minSdk = 19
        targetSdk = 19
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
}
}

dependencies {
    implementation(libs.androidx.media)
    implementation(libs.androidx.media2.session)
    implementation(libs.androidx.media3.exoplayer)
}


