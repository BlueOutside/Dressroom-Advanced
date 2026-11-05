plugins { id("com.android.application") }
android {
    namespace = "dev.denver.dressroomadvanced"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.denver.dressroomadvanced"
        minSdk = 29
        targetSdk = 36
        versionCode = 135
        versionName = "0.21.2-alpha"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildTypes { release { isMinifyEnabled = false } }
}
dependencies { compileOnly("io.github.libxposed:api:102.0.0") }
