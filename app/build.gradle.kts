
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "gov.ny.its.arcGisReplicaPOC"
    compileSdk = 36

    defaultConfig {
        applicationId = "gov.ny.its.ArcGisReplicaPOC"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "ARCGIS_API_KEY",
            "\"AAPTAp5_vZ2McIf0ZCt6YD3PhfflLQwZMPeBym2GyfDOO85vd-5HBhdF7g3rX0BfyH1OzM9dHcl_91invEeLEM2-WiGzeYIvfUYYTZlhJ7uyOu6QAKuyyu_Ox0nD2f7a6mIka8eU3qnPrQhC5nTlf6IEQuI7tURoP0MZCihYoOTovZDkvHauedAaOxsD6cKR-6gzhrjt946V5T1PhN8392XV_ljUVJBMH0iyWPHAcoHG-49fLuAwo4O6VnLIATWiRukjAT1_MxDup7yo\""
        )
        buildConfigField("String", "ARCGIS_CLIENT_ID", "\"SFTSO6bKMxDup7yo\"")
        buildConfigField("String", "ARCGIS_OAUTH_CLIENT_ID", "\"pAwNvXYqirFG3ODC\"")
    }

    viewBinding {
        enable
    }
    buildFeatures {
        buildConfig = true
        compose =  true
        viewBinding = true
        dataBinding =  true
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation(libs.arcgis)

    // Toolkit (provides composable MapView/SceneView via geoview-compose)
    implementation(platform(libs.arcgis.toolkit.bom))
    implementation(libs.arcgis.toolkit.geoview.compose)
    implementation(libs.arcgis.toolkit.authentication)

    implementation("androidx.navigation:navigation-compose:2.9.6")

}