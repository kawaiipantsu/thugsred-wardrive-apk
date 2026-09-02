plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    // Screenshot tests (JVM, no emulator) via Robolectric.
    // 1.46.1 is the last release with Kotlin 1.9 metadata (readable by our 2.0 compiler).
    id("io.github.takahirom.roborazzi") version "1.46.1" apply false
}
