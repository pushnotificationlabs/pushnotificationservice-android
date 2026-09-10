plugins {
    id("com.android.library") version "8.5.0" apply false
    kotlin("android") version "1.9.24" apply false
    // Publishes to Maven Central via the Sonatype Central Portal. Sonatype
    // has no official Gradle plugin for this (their docs say so directly);
    // this is the de facto community standard. The old approach — a raw
    // maven-publish repository pointed at s01.oss.sonatype.org — stopped
    // working when OSSRH was fully sunset on 2025-06-30; that legacy
    // staging API no longer exists.
    // Pinned to 0.34.0, not latest: 0.35.0+ needs Gradle 8.13+, 0.36.0+
    // needs Gradle 9.0+/AGP 8.13+, and this project is on Gradle 8.7 / AGP
    // 8.5.0 — bumping those just to chase the newest publish plugin wasn't
    // worth destabilizing an already-passing, already-CI-verified test
    // suite. 0.34.0 is the last version compatible with this project's
    // actual toolchain (min Gradle 8.5). Revisit if/when Gradle/AGP get
    // bumped for their own reasons.
    id("com.vanniktech.maven.publish") version "0.34.0" apply false
}
