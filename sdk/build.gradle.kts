plugins {
    id("com.android.library")
    kotlin("android")
    id("com.vanniktech.maven.publish")
}

android {
    namespace = "com.pushnotificationservice.sdk"
    compileSdk = 34

    defaultConfig {
        minSdk = 23
        targetSdk = 34
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    // No android.publishing.singleVariant("release") here: the
    // com.vanniktech.maven.publish plugin's AndroidSingleVariantLibrary
    // component registers the "release" variant itself. Declaring both
    // throws "Using singleVariant publishing DSL multiple times."
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.json:json:20240303")
}

// Credentials/signing key are read by convention from Gradle properties or
// ORG_GRADLE_PROJECT_-prefixed env vars — mavenCentralUsername/Password
// (a Central Portal user token, NOT your account login) and
// signingInMemoryKey/KeyId/KeyPassword — so nothing here references
// System.getenv() directly; the plugin wires that up itself. Locally,
// running ./gradlew publish without these set fails loudly rather than
// silently publishing unsigned.
mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates("com.pushnotificationservice", "android-sdk", project.findProperty("sdkVersion") as? String ?: "0.1.0")

    pom {
        name.set("PushNotificationService Android SDK")
        description.set("Client SDK for PushNotificationService.com native device-token registration and notification handling.")
        url.set("https://github.com/pushnotificationlabs/pushnotificationservice-android")
        licenses {
            license {
                name.set("MIT")
                url.set("https://opensource.org/licenses/MIT")
            }
        }
        developers {
            developer {
                id.set("pushnotificationlabs")
                name.set("PushNotificationService.com")
                email.set("hello@pushnotificationservice.com")
            }
        }
        scm {
            connection.set("scm:git:https://github.com/pushnotificationlabs/pushnotificationservice-android.git")
            developerConnection.set("scm:git:https://github.com/pushnotificationlabs/pushnotificationservice-android.git")
            url.set("https://github.com/pushnotificationlabs/pushnotificationservice-android")
        }
    }
}
