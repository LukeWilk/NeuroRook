[![Kover Coverage](https://github.com/lukewilk/NeuroRook/actions/workflows/kover.yml/badge.svg)](https://github.com/lukewilk/NeuroRook/actions/workflows/kover.yml) [![MIT License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

<img src="./resources/neuroRook.svg" width="140" class="center">

## NeuroRook

NeuroRook is a neurofeedback suite based on the [BrainFlow library](https://brainflow.org/). It is provided as-is and is currently in the initial stages of development. The primary target is desktop (JVM), with Android support planned for the future.

---

### Build and Run

#### Desktop (JVM)
Note: Not implemented yet, under development.

To build and run the desktop application:
- Build:
  ```shell
  ./gradlew :composeApp:build
  ```
- Run:
  ```shell
  ./gradlew :composeApp:run
  ```

#### Android
Note: Not implemented yet, but the structure is in place for future development.

To build the Android app:
- Build:
  ```shell
  ./gradlew :androidApp:assembleDebug
  ```
- Install and run via Android Studio or adb.

#### iOS
Note: Not implemented yet, but the structure is in place for future development.

To build and run the iOS app:
- Open the `/iosApp/iosApp` directory in Xcode and run from there.

---

### Project Structure

* [`/composeApp`](./composeApp/src): Shared code for Compose Multiplatform applications.
* [`/iosApp`](./iosApp/iosApp): Entry point for iOS applications, including SwiftUI code.

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)

---

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
