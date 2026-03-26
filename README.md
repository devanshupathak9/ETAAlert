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

Everything looks correct. All constraint chains are intact. Here's a summary of all changes made:                                  
                                                                                                                                     
  ---                                                                                     
  1. API Key Validation (ApiKeyActivity.kt + activity_api_key.xml)                                                                   
  - Button now says "Verify & Continue" — makes a real test Directions API call before saving                                        
  - Shows a spinner while validating                                                                                                 
  - Invalid/expired key → AlertDialog: "Invalid or expired API key, or Directions API not enabled" with instructions                 
  - Quota exceeded → AlertDialog: billing issue message                                                                              
  - Network error → offers "Save Anyway" option so you're not blocked offline                                                        
  - Valid key → toast "API key verified!" then proceeds                                                                              
                                                                                                                                     
  2. Destination search with Places autocomplete (SetupActivity.kt + activity_setup.xml)                                           
  - Destination field is now a MaterialAutoCompleteTextView                                                                          
  - After typing 2+ characters, waits 400ms then calls the Places Autocomplete API                                                   
  - Suggestions appear as a dropdown — biased to your current location (nearby results first)                                        
  - Tap any suggestion to fill the field                                                                                             
                                                                                                                                     
  3. Current location bar above destination (activity_setup.xml + SetupActivity.kt)                                                  
  - A subtle pill-shaped row shows: From: [your current address]                                                                     
  - Auto-detects on screen open using FusedLocationProvider + reverse geocoding                                                      
  - Refresh button (↺) to re-detect if you've moved                                                                                  
  - Falls back to lat, lng coordinates if geocoding API isn't enabled                                                                
                                                                                                                                     
  Note: The Places and Geocoding APIs need to be enabled in Google Cloud Console alongside the existing Directions API. The updated  
  API key description screen now mentions all three required APIs.