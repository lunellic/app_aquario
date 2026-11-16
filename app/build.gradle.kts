plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Versao: tag v1.2.3 no CI vira versionName 1.2.3; fora de tag usa 0.0.<run>.
val ciRun = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
val ciTag = (System.getenv("RELEASE_TAG")?.takeIf { it.isNotBlank() } ?: System.getenv("GITHUB_REF_NAME"))
    ?.takeIf { it.startsWith("v") }?.removePrefix("v")

// Keystore fixa vinda de GitHub Secrets (KEYSTORE_BASE64 decodificada pelo workflow).
val keystorePath: String? = System.getenv("KEYSTORE_FILE")?.takeIf { it.isNotBlank() && file(it).exists() }

android {
    namespace = "br.com.lunelli.lulucoreef"
    compileSdk = 35

    defaultConfig {
        applicationId = "br.com.lunelli.lulucoreef"
        minSdk = 22
        targetSdk = 35
        versionCode = ciRun
        versionName = ciTag ?: "0.0.$ciRun"
        buildConfigField("String", "SUPABASE_URL", "\"${property("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_KEY", "\"${property("SUPABASE_PUBLISHABLE_KEY")}\"")
    }

    signingConfigs {
        if (keystorePath != null) {
            create("luluco") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        val signing = signingConfigs.findByName("luluco") ?: signingConfigs.getByName("debug")
        debug { signingConfig = signing }
        release {
            // Sem R8 por enquanto: prioridade e nao quebrar no Fire. Release (nao debuggable) ja roda bem mais leve que debug.
            isMinifyEnabled = false
            signingConfig = signing
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true // java.time no Android 5.1
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    lint { abortOnError = false }
}

dependencies {
    // Versoes fixadas: ultimas estaveis que ainda aceitam minSdk 21/22 (ver README).
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
    implementation(platform("androidx.compose:compose-bom:2025.05.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
