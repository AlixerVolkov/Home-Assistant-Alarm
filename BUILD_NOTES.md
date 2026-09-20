# Build notes

Source validation performed in the generation environment:

- All Android XML files parse successfully.
- All `R.string.*` references used by Kotlin sources exist in the default resource set.
- English and Spanish string resource keys are in sync.
- Pure Kotlin domain models compile with the available Kotlin compiler.
- A syntax-only pass over all Kotlin source files reported no parser errors; unresolved Android/Compose symbols are expected because this environment has no Android SDK or Gradle dependency cache.

A full Android `assembleDebug` was not executed because the generation environment does not contain an Android SDK or Gradle and cannot download binary build dependencies from the container network.

The project pins AGP 9.4.0 and Gradle 9.6.0 in its build files. `gradle-wrapper.properties` is included, but the generated Gradle wrapper scripts/JAR are not included because the binary wrapper could not be retrieved in this environment.

Recommended first verification after opening in Android Studio:

1. Select JDK 17 for Gradle.
2. Install Android SDK 37.
3. Let Android Studio sync the project.
4. Generate/update the Gradle wrapper if required by your local IDE.
5. Run `:app:assembleDebug`.
