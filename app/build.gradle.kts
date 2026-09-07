import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val licenseSecret: String = localProps.getProperty("license.secret")
    ?: throw GradleException("Добавьте license.secret=... в local.properties (см. tools/generate_key.py)")

android {
    namespace = "com.voxa.dictation"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.voxa.dictation"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
        buildConfigField("String", "LICENSE_SECRET", "\"$licenseSecret\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"voxa-a99ac\"")
        buildConfigField("String", "FIREBASE_API_KEY", "\"AIzaSyBGTpDZxFINBOdEf8Y9bXqFs7c5iU0B3jI\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
}
