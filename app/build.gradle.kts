plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.maka.xiaoxia"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.maka.xiaoxia"
        minSdk = 19
        targetSdk = 35
        versionCode = 3
        versionName = "0.1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/java")
            kotlin.srcDirs("src/main/kotlin")
        }
    }

    applicationVariants.all {
        val variant = this
        variant.outputs
            .map { it as com.android.build.gradle.internal.api.BaseVariantOutputImpl }
            .forEach { output ->
                val outputFileName = "xiaoxiamusic-v${variant.versionName}-${variant.buildType.name}.apk"
                output.outputFileName = outputFileName
            }
    }
}

dependencies {
    // Android 4.4兼容依赖 - 使用安卓15兼容版本
    implementation("androidx.appcompat:appcompat:1.4.2")
    implementation("androidx.core:core:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    // 移除Material Design依赖，使用原生Android组件
    
    // 媒体支持库 - 车机控制
    implementation("androidx.media:media:1.6.0")
    
    // Gson用于数据序列化
    implementation("com.google.code.gson:gson:2.8.9")
    
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}