import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    id("org.jetbrains.kotlin.plugin.parcelize")
}

android {
    namespace = "com.nekolaska.Builder"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.nekolaska.Builder"
        minSdk = 26
        targetSdk = 37
        versionCode = 9
        versionName = "1.2.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
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
    kotlin {
        jvmToolchain(17)
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    applicationVariants.all {
        outputs.all {
            val currentDateTime = LocalDateTime.now()
            // 定义时间格式
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH_mm_ss")
            // 格式化时间并打印输出
            val formattedDateTime = currentDateTime.format(formatter)
            val ver = defaultConfig.versionName
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                "Builder-$formattedDateTime-$ver.APK";
        }
    }
}

// The legacy Luaj++ JAR omits StackMapTable entries. ART accepts it; local unit tests do not.
tasks.withType<Test>().configureEach {
    jvmArgs("-noverify")
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)

    //AndroidX
    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.collection)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.lifecycle.common)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.startup.runtime)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.transition)
    implementation(libs.androidx.window)

    //Material
    implementation(libs.material)

    implementation(libs.library)

    implementation(libs.coil)
    implementation(libs.coil.svg)
    implementation(libs.coil.gif)
}
