plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android { namespace = "com.olgun.kitaplik"; compileSdk = 35
 defaultConfig { applicationId = "com.olgun.kitaplik"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "1.0" } }
kotlin { jvmToolchain(17) }