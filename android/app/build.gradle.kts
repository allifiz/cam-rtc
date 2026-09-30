plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
 namespace = "dev.camrtc"
 compileSdk = 35
 defaultConfig { applicationId = "dev.camrtc"; minSdk = 26; targetSdk = 35; versionCode = 2; versionName = "0.1.1" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}
dependencies {
 implementation("io.github.webrtc-sdk:android:144.7559.05")
 implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
