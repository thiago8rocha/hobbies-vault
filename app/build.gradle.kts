plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.ksp)
}

// Conta de commits usada para nomear builds nightly (r<N>). Fora de um repo git
// (ex.: antes do primeiro `git init`), cai para "0" em vez de quebrar o build.
fun runCommandOrDefault(command: String, default: String): String =
    runCatching {
        providers.exec { commandLine(command.split(" ")) }.standardOutput.asText.get().trim()
    }.getOrElse { default }

val commitCount by lazy { runCommandOrDefault("git rev-list --count HEAD", "0") }

android {
    namespace   = "com.hobbiesvault"
    compileSdk  = 35

    defaultConfig {
        applicationId = "com.hobbiesvault"
        minSdk        = 34
        targetSdk     = 35
        versionCode   = 1
        versionName   = "1.0.0"
    }

    buildTypes {
        debug {
            // Sufixo próprio para instalar lado a lado com a versão de produção sem
            // conflito de assinatura/dados — importante para APKs de teste (ex.: com
            // dados fake) em um celular que já tem o app "de verdade" instalado.
            applicationIdSuffix = ".debug"
            versionNameSuffix   = "-debug"
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // Build de pré-lançamento gerado a cada push em master pelo CI (workflow build_push.yml).
        // Assinada com a chave de debug (sem depender de secrets) e instalável lado a lado com
        // a versão estável graças ao applicationIdSuffix.
        create("nightly") {
            initWith(getByName("release"))
            matchingFallbacks += "release"
            applicationIdSuffix = ".nightly"
            versionNameSuffix   = "-r$commitCount"
            signingConfig       = signingConfigs.getByName("debug")
        }
        // Build de pré-lançamento disparada manualmente (workflow_dispatch), assinada em CI
        // com a keystore de release real via secrets — sem signingConfig aqui de propósito,
        // o Gradle produz um APK unsigned e o step "Sign APK" do build_push.yml assina depois.
        create("beta") {
            initWith(getByName("release"))
            matchingFallbacks += "release"
        }
        // Build minificada (menor que a "debug" pura) para distribuir APKs de teste ad hoc —
        // ex.: builds com dados fake para testes de usabilidade fora do time de dev. Assinada
        // com a chave de debug e isDebuggable=true para poder rodar código guardado por
        // BuildConfig.DEBUG (como o DebugSeeder).
        create("qa") {
            initWith(getByName("release"))
            matchingFallbacks += "release"
            applicationIdSuffix = ".qa"
            versionNameSuffix   = "-qa"
            signingConfig       = signingConfigs.getByName("debug")
            isDebuggable        = true
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
        compose     = true
        buildConfig = true
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }

    androidResources {
        // Prevent aapt from re-compressing already-gzipped assets (stored as .bin)
        noCompress += "bin"
    }
}

dependencies {
    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.activity)
    debugImplementation(libs.compose.ui.tooling)

    // Navigation
    implementation(libs.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // WorkManager
    implementation(libs.workmanager)

    // Lifecycle
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    // Networking
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Images
    implementation(libs.coil)

    // DataStore
    implementation(libs.datastore)

    // Charts
    implementation(libs.vico.compose)

    // Shimmer
    implementation(libs.shimmer)

    // ML Kit Translation
    implementation(libs.mlkit.translate)

    // JSON
    implementation(libs.gson)

    // Coroutines
    implementation(libs.coroutines.android)

    // AppCompat (required for theme bridge with Compose)
    implementation(libs.appcompat)

    // Testes unitários (JVM, sem dependência de Android/emulador)
    testImplementation(libs.junit)
}
