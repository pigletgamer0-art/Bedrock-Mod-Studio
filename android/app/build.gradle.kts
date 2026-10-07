plugins {
    id("com.android.application")
}

android {
    namespace = "com.bedrockmodstudio.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.bedrockmodstudio.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 3
        versionName = "0.1.3-alpha"
    }

    signingConfigs {
        getByName("debug") {
            val stableDebugKeystore = file("bms-debug.keystore")
            if (stableDebugKeystore.exists()) {
                storeFile = stableDebugKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }

        create("release") {
            val releaseKeystore = file("bms-release.keystore")
            if (releaseKeystore.exists()) {
                storeFile = releaseKeystore
                storePassword = System.getenv("BMS_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("BMS_KEY_ALIAS")
                keyPassword = System.getenv("BMS_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            val releaseKeystore = file("bms-release.keystore")
            if (releaseKeystore.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core:1.17.0")
    implementation("androidx.webkit:webkit:1.17.1")
}

val webRoot = rootProject.projectDir.parentFile
val generatedWebAssets = layout.buildDirectory.dir("generated/bmsWebAssets").get().asFile
val generatedWebWww = generatedWebAssets.resolve("www")

val syncWebAssets = tasks.register<Copy>("syncWebAssets") {
    from(webRoot) {
        include("index.html")
        include("styles.css")
        include("app.js")
        include("manifest.webmanifest")
        include("sw.js")
        include("bundle/**")
    }
    into(generatedWebWww)
}

android.sourceSets.getByName("main").assets.srcDir(generatedWebAssets)

tasks.configureEach {
    if (name.startsWith("merge") && name.endsWith("Assets")) {
        dependsOn(syncWebAssets)
    }
}
