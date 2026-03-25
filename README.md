# ETAAlert MVP — Project Overview

This project is a complete MVP for **ETAAlert**, an Android application designed to track travel duration in real-time and alert users based on traffic conditions and specific time thresholds.

---

## 📊 File Breakdown
The project consists of **31 files**. Below is the map of the core components and their responsibilities:

| File | Description / Responsibility |
| :--- | :--- |
| **build.gradle (project)** | Configured with AGP 8.2.0 and Kotlin 1.9.22. |
| **app/build.gradle** | Includes dependencies for OkHttp, Coroutines, WorkManager, Play Location, Gson, and ViewBinding. |
| **AndroidManifest.xml** | Contains all necessary permissions, service registrations, and activity declarations. |
| **MainActivity.kt** | The entry point; handles routing to `ApiKey`, `Setup`, or `Status` based on the current app state. |
| **ApiKeyActivity.kt** | Manages one-time Google Maps API key entry and validation. |
| **SetupActivity.kt** | Handles destination, threshold, and duration inputs; manages permission flows and service ignition. |
| **ui/StatusActivity.kt** | Displays live ETA with a green/red status indicator using a `LocalBroadcastReceiver`. |
| **data/AppPreferences.kt** | A `SharedPreferences` wrapper managing all persistent app states. |
| **data/DirectionsRepository.kt** | Uses OkHttp to query the Directions API and parses `duration_in_traffic`. |
| **data/LocationRepository.kt** | Interface for `FusedLocationProvider` utilizing `suspendCancellableCoroutine`. |
| **domain/EtaEvaluator.kt** | Pure business logic providing `EtaResult` (minutes, alert status, expiration). |
| **service/EtaForegroundService.kt** | Manages a 4-minute coroutine polling loop, wake locks, and dual notification channels. |
| **service/EtaWorker.kt** | A 15-minute `WorkManager` heartbeat to ensure the service restarts if killed by the OS. |
| **UI Resources** | Includes all layouts, drawables, strings, colors, and themes for a complete UI. |

---

## 🚀 How to Run

Follow these steps to get the MVP running on your environment:

1.  **Open Project:** Launch Android Studio and open the project folder.
2.  **Gradle Sync:** Allow Android Studio to sync Gradle files and download dependencies.
3.  **Deployment:** Build and install the app on a physical device or emulator (Minimum **API 26+**).
4.  **Configuration:** Upon first launch, enter a valid **Google Maps Directions API key** to enable tracking features.

---

Would you like me to help you draft a `README.md` specifically for the GitHub repository, or perhaps generate a `.gitignore` file for this project?