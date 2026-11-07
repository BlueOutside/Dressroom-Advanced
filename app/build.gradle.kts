plugins { id("com.android.application") }
android {
    namespace = "dev.denver.dressroomadvanced"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.denver.dressroomadvanced"
        minSdk = 36
        targetSdk = 36
        versionCode = 1010
        versionName = "1.0.10-fold8-test"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildTypes { release { isMinifyEnabled = false } }
}
dependencies { compileOnly("io.github.libxposed:api:102.0.0") }
