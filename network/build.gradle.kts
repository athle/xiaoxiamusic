plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.xiaoxiamusic.network"
    compileSdk = 34

    defaultConfig {
        minSdk = 19
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
    implementation(libs.okhttp)
    implementation(libs.sardine) {
        exclude(group = "com.squareup.okhttp3", module = "okhttp")
    }
    implementation("com.github.thegrizzlylabs:sardine-android:0.8")
}