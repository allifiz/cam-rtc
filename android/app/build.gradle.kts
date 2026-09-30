plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
 namespace = "dev.camrtc"
 compileSdk = 35
 defaultConfig { applicationId = "dev.camrtc"; minSdk = 26; targetSdk = 35; versionCode = 3; versionName = "0.2.0" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 testOptions { unitTests.all { it.testLogging { exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL; showStandardStreams = true } } }
 kotlinOptions { jvmTarget = "17" }
}
dependencies {
 testImplementation("junit:junit:4.13.2")
 implementation("io.github.webrtc-sdk:android:144.7559.05")
 implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
