import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin: no Android dependencies, so the circadian model compiles and tests on the JVM.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = AndroidConfig.JAVA_VERSION
    targetCompatibility = AndroidConfig.JAVA_VERSION
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(AndroidConfig.JAVA_VERSION.toString()))
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

dependencies {
    // Exposed because repository interfaces use Flow in their signatures.
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
