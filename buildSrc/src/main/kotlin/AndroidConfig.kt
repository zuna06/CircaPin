import org.gradle.api.JavaVersion

/** Build settings shared by every module. */
object AndroidConfig {
    const val COMPILE_SDK = 37
    const val TARGET_SDK = 37
    const val MIN_SDK = 28

    val JAVA_VERSION = JavaVersion.VERSION_17
}
