import java.io.FileInputStream
import java.util.Properties

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        load(FileInputStream(keystorePropertiesFile))
    }
}

fun gradleStringProperty(name: String, envName: String? = null, default: String = ""): String {
    return providers.gradleProperty(name).orNull
        ?: envName?.let(System::getenv)
        ?: default
}

fun gradleBooleanProperty(name: String, envName: String? = null, default: Boolean): Boolean {
    val raw = gradleStringProperty(name, envName, default.toString())
    return raw.equals("true", ignoreCase = true)
}

fun quoteBuildConfig(value: String): String = buildString {
    append('"')
    value.forEach { ch ->
        when (ch) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(ch)
        }
    }
    append('"')
}

val kirariOidcClientId = gradleStringProperty(
    name = "hanakoKirariOidcClientId",
    envName = "HANAKO_KIRARI_OIDC_CLIENT_ID"
)
val kirariServerUrl = gradleStringProperty(
    name = "hanakoKirariServerUrl",
    envName = "HANAKO_KIRARI_SERVER_URL"
)
val kirariServerUrlEditable = gradleBooleanProperty(
    name = "hanakoKirariServerUrlEditable",
    envName = "HANAKO_KIRARI_SERVER_URL_EDITABLE",
    default = true
)
val showDebugLogs = gradleBooleanProperty(
    name = "hanakoShowDebugLogs",
    envName = "HANAKO_SHOW_DEBUG_LOGS",
    default = true
)
val showKirariEntry = gradleBooleanProperty(
    name = "hanakoShowKirariEntry",
    envName = "HANAKO_SHOW_KIRARI_ENTRY",
    default = true
)

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "fun.kirari.hanako"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "fun.kirari.hanako"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "KIRARI_OIDC_CLIENT_ID", quoteBuildConfig(kirariOidcClientId))
        buildConfigField("String", "KIRARI_SERVER_URL", quoteBuildConfig(kirariServerUrl))
        buildConfigField("boolean", "KIRARI_SERVER_URL_EDITABLE", kirariServerUrlEditable.toString())
        buildConfigField("boolean", "SHOW_DEBUG_LOGS", showDebugLogs.toString())
        buildConfigField("boolean", "SHOW_KIRARI_ENTRY", showKirariEntry.toString())

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "edition"
    productFlavors {
        create("full") {
            dimension = "edition"
            buildConfigField("boolean", "HAS_MLKIT", "true")
        }
        create("lite") {
            dimension = "edition"
            buildConfigField("boolean", "HAS_MLKIT", "false")
        }
    }

    androidResources {
        localeFilters += "zh"
    }

    if (keystorePropertiesFile.exists()) {
        signingConfigs {
            create("release") {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        aidl = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":kirari-auth-core"))
    implementation(project(":kirari-llm-core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.savedstate)
    implementation(libs.androidx.datastore.preferences)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.jetbrains.markdown)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.jlatexmath)
    implementation(libs.jlatexmath.font.greek)
    implementation(libs.jlatexmath.font.cyrillic)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    "fullImplementation"(libs.mlkit.text.recognition.chinese)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
