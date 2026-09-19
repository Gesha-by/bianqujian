import java.util.Properties

plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val amapWebKey = localProperties.getProperty("AMAP_WEB_KEY", "")

android { namespace = "com.bianqujian.app"; compileSdk = 35
    defaultConfig { applicationId = "com.bianqujian.app"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "0.1.0" }
    buildFeatures { buildConfig = true }
    defaultConfig { buildConfigField("String", "AMAP_WEB_KEY", "\"$amapWebKey\"") }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.0")
}
