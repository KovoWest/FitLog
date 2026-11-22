plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {

    namespace = "com.syw.figlogsample"

    compileSdk = 36

    defaultConfig {

        applicationId = "com.syw.figlogsample"

        minSdk = 26

        targetSdk = 36

        versionCode = 1

        versionName = "1.0"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {

        release {

            isMinifyEnabled = false

            proguardFiles(
                getDefaultProguardFile(
                    "proguard-android-optimize.txt"
                ),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {

        sourceCompatibility =
            JavaVersion.VERSION_11

        targetCompatibility =
            JavaVersion.VERSION_11
    }

    kotlinOptions {

        jvmTarget = "11"
    }
}

dependencies {

    // Kotlin 및 Android 기본 기능
    implementation(libs.androidx.core.ktx)

    // AppCompat
    implementation(libs.androidx.appcompat)

    // Material Design
    implementation(libs.material)
    implementation("androidx.cardview:cardview:1.0.0")

    // Activity
    implementation(libs.androidx.activity)

    // ConstraintLayout
    implementation(libs.androidx.constraintlayout)

    // 단위 테스트
    testImplementation(libs.junit)

    // Android 테스트
    androidTestImplementation(libs.androidx.junit)

    // UI 테스트
    androidTestImplementation(libs.androidx.espresso.core)
}