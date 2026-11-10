import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releaseVersion = (findProperty("notevidVersionName") as String?) ?: "1.0.1"
val releaseCode = (findProperty("notevidVersionCode") as String?)?.toIntOrNull() ?: 1_000_001
val githubRepo = (findProperty("githubRepo") as String?) ?: "OWNER/NoteVid"
val keystoreFile = System.getenv("NOTEV_ID_KEYSTORE_FILE")
val envKeyAlias = System.getenv("NOTEV_ID_KEY_ALIAS")
val envKeyPass = System.getenv("NOTEV_ID_KEY_PASSWORD")
val envStorePass = System.getenv("NOTEV_ID_STORE_PASSWORD")
val canSign = listOf(keystoreFile, envKeyAlias, envKeyPass, envStorePass).all { !it.isNullOrBlank() }

android {
    namespace = "com.notevid.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.notevid.app"
        minSdk = 26
        targetSdk = 35
        versionCode = releaseCode
        versionName = releaseVersion
        buildConfigField("String", "GITHUB_REPO", "\"${githubRepo.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        if (canSign) {
            create("releaseNoteVid") {
                storeFile = File(keystoreFile!!)
                storePassword = envStorePass
                keyAlias = envKeyAlias
                keyPassword = envKeyPass
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (canSign) signingConfig = signingConfigs.getByName("releaseNoteVid")
        }
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.core:core-ktx:1.16.0")
    testImplementation("junit:junit:4.13.2")
}
