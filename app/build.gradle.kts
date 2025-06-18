plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.gms.google.services)
    id("kotlin-parcelize")
}

android {
    namespace = "com.example.trabajointegradornativo"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.trabajointegradornativo"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.google.firebase.firestore.ktx)
    implementation("com.google.firebase:firebase-storage:20.3.0")
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.5")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.5")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.core:core-ktx:1.12.0")
    // Asegúrate de tener también estas (probablemente ya las tienes)
    implementation ("com.google.firebase:firebase-auth:22.3.0")
    implementation ("com.google.firebase:firebase-firestore:24.10.0")
    // LÍNEA CORREGIDA - agregadas las comillas
    implementation("androidx.cardview:cardview:1.0.0")
    implementation(libs.places)
    implementation(libs.firebase.dataconnect)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}