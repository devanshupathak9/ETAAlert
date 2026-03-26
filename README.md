# ETA Alert — Android App

ETA Alert is an Android application that monitors real-time traffic and notifies you at the exact moment it's time to leave for your destination. Instead of repeatedly checking Google Maps, you set a destination, an ETA threshold, and a tracking window — the app does the rest in the background and fires a single push notification when the drive time drops below your target.

---

## How It Works

1. Open the app and enter your destination, ETA threshold (e.g. "alert me when under 18 min"), and how long to keep tracking (e.g. 60 minutes).
2. The app starts a **Foreground Service** that holds a wake lock and polls traffic every ~4 minutes.
3. Each poll:
   - Gets your current location via `FusedLocationProvider`
   - Calls the Google Directions API with `departure_time=now` for live traffic data
   - Compares the result against your threshold
4. The moment `ETA ≤ threshold` → a high-priority push notification fires → tracking stops automatically.
5. If the tracking window expires before the condition is met → the service stops silently.
6. **WorkManager** runs a 15-minute heartbeat to restart the service if the OS kills it.

---

## Core Features

- **Destination search** — autocomplete suggestions biased to your current location
- **Live "From" location** — auto-detected with reverse geocoding; refresh button available
- **Threshold alert** — one-shot notification when ETA drops below X minutes
- **Time-bound tracking** — fixed duration options (30 / 60 / 90 / 120 min); auto-stops when expired
- **Status screen** — live green (good to go) / red (not yet) indicator updated by the background service
- **User-provided API key** — validated on entry; stored locally; no backend required
- **WorkManager fallback** — restarts the foreground service if killed by aggressive OEMs

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| Background execution | Android Foreground Service + WorkManager |
| Location | FusedLocationProviderClient (Google Play Services) |
| Traffic data | Google Directions API (`departure_time=now`) |
| Place search | Google Places Autocomplete API + Geocoding API |
| Local storage | SharedPreferences |
| Notifications | NotificationManager (Android standard) |
| Networking | OkHttp + Gson |
| Min SDK | Android 8.0 (API 26) |

---

## Folder Structure

```
ETAAlert/
├── build.gradle                          # Root Gradle — AGP 8.2.0, Kotlin 1.9.22
├── settings.gradle
├── gradle.properties
├── gradlew / gradlew.bat
├── README.md
├── FIXES.md
│
└── app/
    ├── build.gradle                      # minSdk 26, targetSdk 34, ViewBinding enabled
    ├── proguard-rules.pro
    │
    └── src/main/
        ├── AndroidManifest.xml           # Permissions, service/activity declarations
        │
        ├── kotlin/com/etaalert/
        │   ├── MainActivity.kt           # Entry-point router → ApiKey / Setup / Status
        │   ├── ApiKeyActivity.kt         # API key entry, validation, inline status feedback
        │   ├── SetupActivity.kt          # Destination, threshold, duration; starts service
        │   │
        │   ├── ui/
        │   │   └── StatusActivity.kt     # Live ETA display; receives broadcasts from service
        │   │
        │   ├── service/
        │   │   ├── EtaForegroundService.kt  # 4-min polling loop, wake lock, notifications
        │   │   └── EtaWorker.kt             # WorkManager 15-min heartbeat / restarter
        │   │
        │   ├── data/
        │   │   ├── AppPreferences.kt        # SharedPreferences wrapper (all persisted state)
        │   │   ├── DirectionsRepository.kt  # Directions API client (traffic-aware ETA)
        │   │   ├── LocationRepository.kt    # FusedLocationProvider wrapper
        │   │   └── PlacesRepository.kt      # Places Autocomplete + Geocoding API client
        │   │
        │   └── domain/
        │       └── EtaEvaluator.kt          # Pure logic: shouldAlert, trackingExpired
        │
        └── res/
            ├── layout/
            │   ├── activity_main.xml        # Splash / routing screen
            │   ├── activity_api_key.xml     # API key setup UI
            │   ├── activity_setup.xml       # Main page: destination, threshold, duration
            │   └── activity_status.xml      # Live tracking status UI
            │
            ├── drawable/                    # Vector icons (location, refresh, circles, etc.)
            ├── values/
            │   ├── strings.xml             # All UI strings
            │   ├── colors.xml              # Brand and status colors
            │   └── themes.xml              # Material Design 3 / NoActionBar theme
            └── mipmap-anydpi-v26/          # Adaptive launcher icons
```

---

## Key Files Explained

| File | Responsibility |
|---|---|
| `MainActivity.kt` | Reads app state and routes to the right screen on launch |
| `ApiKeyActivity.kt` | Validates the key via a real Directions API call; shows inline success/error |
| `SetupActivity.kt` | Collects user inputs; checks API key before starting; manages permission flow |
| `StatusActivity.kt` | Subscribes to `LocalBroadcastManager` updates from the foreground service |
| `EtaForegroundService.kt` | Core engine — coroutine polling loop, wake lock, dual notification channels |
| `EtaWorker.kt` | WorkManager periodic task that restarts the service if the OS kills it |
| `DirectionsRepository.kt` | OkHttp call to Directions API; parses `duration_in_traffic` |
| `LocationRepository.kt` | `suspendCancellableCoroutine` wrapper around FusedLocationProvider |
| `PlacesRepository.kt` | Autocomplete suggestions + reverse geocoding calls |
| `EtaEvaluator.kt` | Pure function: returns `shouldAlert` and `trackingExpired` booleans |
| `AppPreferences.kt` | Single source of truth for all persisted data (key, destination, threshold, etc.) |

---

## Permissions Required

```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

---

## Google Maps API Setup

Users provide their own API key. Required APIs to enable in Google Cloud Console:

- **Directions API** — traffic-aware ETA
- **Places API** — destination autocomplete suggestions
- **Geocoding API** — reverse geocoding for the "From" location label

Free tier: $200/month credit ≈ 40,000 Directions API calls. More than sufficient for personal use.

---

## How to Run

1. Open the project in Android Studio.
2. Let Gradle sync and download dependencies.
3. Build and install on a physical device or emulator (API 26+).
4. On first launch, enter a valid Google Maps API key to enable all features.

---

## Changelog

### Change 1 — Initial MVP

- `MainActivity.kt` — entry-point router: no API key → ApiKeyActivity, tracking active → StatusActivity, otherwise → SetupActivity
- `ApiKeyActivity.kt` — one-time API key entry; validates via a live Directions API test call; shows spinner during validation; handles REQUEST_DENIED, quota exceeded, and network-error states with AlertDialog; "Save Anyway" fallback for offline scenarios
- `SetupActivity.kt` — destination input with Places Autocomplete (2-char threshold, 400ms debounce, location-biased); current location auto-detected and shown in a "From:" bar with a refresh button; threshold and duration text inputs; location and notification permission flows; starts EtaForegroundService and schedules EtaWorker
- `StatusActivity.kt` — live ETA display updated via LocalBroadcastManager; green/red status circles; stop tracking button
- `EtaForegroundService.kt` — foreground service with 4-minute coroutine polling loop, 3-hour partial wake lock, dual notification channels (silent ongoing + high-priority alert)
- `EtaWorker.kt` — WorkManager 15-minute periodic heartbeat to restart the service if killed
- `DirectionsRepository.kt` — OkHttp Directions API client; uses `departure_time=now` and `traffic_model=best_guess`; parses `duration_in_traffic` with 30-second rounding
- `LocationRepository.kt` — FusedLocationProvider wrapper with 10-second timeout fallback
- `PlacesRepository.kt` — Places Autocomplete and Geocoding API client
- `EtaEvaluator.kt` — pure business logic; returns `EtaResult(etaMinutes, shouldAlert, trackingExpired)`
- `AppPreferences.kt` — SharedPreferences wrapper for all persisted state

---

### Change 2 — UI Fixes and Improvements

**1. Destination Input Box**
- Increased `minHeight` to 56dp, `textSize` to 16sp, added top/bottom padding for a taller, more usable field
- `boxStrokeWidth` set to 2dp for better visual definition
- Updated hint text to be more descriptive: *"Search destination (e.g. Times Square, New York)"*

**2. Default Landing Page (SetupActivity)**
- Added a bold primary-color app title "ETA Alert" and subtitle "Know exactly when to leave" at the top of the setup screen
- Main page now clearly shows: destination search, threshold input, duration dropdown, and API key button
- "Change API Key" button upgraded from text-only to an outlined button for better discoverability, relabelled "Add / Update API Key"

**3. Add / Update API Key Page (ApiKeyActivity + activity_api_key.xml)**
- Wrapped layout in `ScrollView` to handle smaller screens
- Added a back `ImageButton` (`ic_arrow_back`) — visible when launched from setup or when a key already exists; calls `finish()` to return to the previous screen
- Added a `MaterialCardView` instructions block with numbered steps: create project → enable APIs → create API Key → paste below
- API key field now has a password-toggle eye icon (`app:endIconMode="password_toggle"`) for show/hide
- `EXTRA_SHOW_BACK` intent extra lets `SetupActivity` pass the back-button flag when launching `ApiKeyActivity`

**4. API Key Validation Feedback**
- Replaced AlertDialog error popups with an inline `tvValidationStatus` TextView below the Verify button
- Success: green text (`#2E7D32`) — *"✓ API key verified successfully!"*; auto-navigates to SetupActivity after 800ms
- Invalid key: red text (`#C62828`) — *"✗ Invalid or expired key. Check your Cloud Console."*
- Quota error: red text with quota message
- Network error: red inline message + Toast offering to retry when online

**5. Duration Dropdown**
- Replaced free-text `TextInputEditText` with a Material ExposedDropdownMenu (`AutoCompleteTextView` + `ExposedDropdownMenu` style)
- Fixed options: **30 minutes, 60 minutes, 90 minutes, 120 minutes**
- Pre-selects the previously saved duration on screen open; defaults to 60 minutes

**6. Search Button — API Key Guard**
- "Search & Start Tracking" button now checks `prefs.getApiKey() == null` before proceeding
- If no key: shows Toast *"Please set up your Google Maps API key first"* and opens `ApiKeyActivity` with the back button enabled
- If key exists: proceeds with destination/threshold/duration validation and starts tracking
