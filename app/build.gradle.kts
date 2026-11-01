plugins { id("com.android.application") }
android {
 namespace = "com.nexus.sidekick"
 compileSdk = 35
 defaultConfig { applicationId = "com.nexus.sidekick"; minSdk = 29; targetSdk = 35; versionCode = 1; versionName = "0.4.0-alpha" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
