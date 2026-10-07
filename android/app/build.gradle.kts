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
        versionCode = 1
        versionName = "0.1.0-alpha"
    }

    buildTypes {
        release {
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
    implementation("androidx.core:core:1.19.1")
    implementation("androidx.webkit:webkit:1.17.1")
}

val webRoot = rootProject.projectDir.parentFile
val generatedWebAssets = layout.buildDirectory.dir("generated/bmsWebAssets")

val syncWebAssets by tasks.registering(Copy::class) {
    from(webRoot) {
        include("index.html")
        include("styles.css")
        include("app.js")
        include("manifest.webmanifest")
        include("sw.js")
        include("bundle/**")
    }
    into(generatedWebAssets.map { it.dir("www") })
}

android.sourceSets.getByName("main").assets.srcDir(generatedWebAssets)

tasks.configureEach {
    if (name.startsWith("merge") && name.endsWith("Assets")) {
        dependsOn(syncWebAssets)
    }
}
