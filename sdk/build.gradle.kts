plugins {
    id("com.android.library")
    kotlin("android")
    id("maven-publish")
    id("signing")
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

    publishing {
        singleVariant("release")
    }
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

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.pushnotificationservice"
            artifactId = "android-sdk"
            version = project.findProperty("sdkVersion") as? String ?: "0.1.0"

            afterEvaluate { from(components["release"]) }

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
            }
        }
    }
    repositories {
        maven {
            name = "sonatype"
            url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials {
                username = (project.findProperty("ossrhUsername") as? String) ?: System.getenv("OSSRH_USERNAME")
                password = (project.findProperty("ossrhPassword") as? String) ?: System.getenv("OSSRH_PASSWORD")
            }
        }
    }
}

signing {
    val signingKey = System.getenv("GPG_SIGNING_KEY")
    val signingPassword = System.getenv("GPG_SIGNING_PASSWORD")
    if (signingKey != null && signingPassword != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications["release"])
    }
}
