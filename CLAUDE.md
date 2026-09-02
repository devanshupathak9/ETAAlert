# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

**ETA Alert** — a single-module Android app (Kotlin) that polls traffic in the background and fires one push notification the moment the drive time to a destination drops below a user-set threshold. Tracking then stops. No backend: the user supplies their own Google Maps API key, stored in `SharedPreferences`, and every request goes device → Google directly.

## Build & Run

Gradle wrapper 8.9, AGP 8.2.0, Kotlin 1.9.22, `minSdk 26` / `targetSdk 34`, ViewBinding enabled (but the code uses `findViewById` throughout — don't assume binding classes exist).

```bash
./gradlew assembleDebug          # build APK
./gradlew installDebug           # build + install on connected device/emulator
./gradlew lint                   # Android Lint
./gradlew clean
```

Requires a JDK and an Android SDK (`local.properties` with `sdk.dir=`, or `ANDROID_HOME`); neither is currently present in this checkout's environment, so Gradle tasks will fail here until they are set up.

There are **no test sources** (`app/src/test` / `app/src/androidTest` do not exist), despite `testInstrumentationRunner` being declared. Adding a test requires creating the source set first. `EtaEvaluator` is the only pure-logic class and is the natural first unit-test target — note it reads `System.currentTimeMillis()` internally, so expiry is not injectable as written.

## Architecture

```
MainActivity ──► StatusActivity   (if prefs.isTracking())
             └─► SetupActivity    (otherwise)

SetupActivity ──► EtaForegroundService (ACTION_START) + EtaWorker.schedule()
                                │
                                │ LocalBroadcastManager
                                ▼
                          StatusActivity
```

- `MainActivity` — router only; `finish()`es immediately.
- `SetupActivity` — destination autocomplete, threshold, duration dropdown, permission flow, starts the service. Also hosts `NoFilterArrayAdapter`, an inner `ArrayAdapter` whose `Filter` is a no-op so Places results are shown verbatim instead of being re-filtered client-side.
- `ApiKeyActivity` — key entry; validates with a live NYC-coordinates round trip against the Routes API. `EXTRA_SHOW_BACK` controls the back button.
- `EtaForegroundService` — the engine. Coroutine polling loop on `Dispatchers.Main` scope, 3-minute interval (`POLL_INTERVAL_MS`), 3-hour partial wake lock, two notification channels: `eta_tracking` (LOW, ongoing, silent) and `eta_alert` (HIGH, alarm ringtone + `USAGE_ALARM`, full-screen intent).
- `EtaWorker` — 15-minute `PeriodicWorkRequest`; restarts the service only if `prefs.isTracking()` and the service isn't in `getRunningServices()`.
- `data/` — `AppPreferences` (all persisted state), `DirectionsRepository`, `LocationRepository`, `PlacesRepository`. No DI; every consumer constructs its own instance.
- `domain/EtaEvaluator` — pure; returns `EtaResult(etaMinutes, shouldAlert, trackingExpired)`.

### Things that will bite you

- **The ETA call is the Routes API v2, not the legacy Directions API.** `DirectionsRepository` POSTs to `routes.googleapis.com/directions/v2:computeRoutes` with `X-Goog-Api-Key` / `X-Goog-FieldMask: routes.duration` and `routingPreference: TRAFFIC_AWARE`; it parses a string duration like `"2930s"`. The class name and the docs are historical. Autocomplete and reverse geocoding still use the classic `maps.googleapis.com` REST endpoints, so a working key needs **Routes API + Places API + Geocoding API** enabled.
- **HTTP 403 from the Routes call means "bad key"** — `DirectionsRepository` throws `InvalidApiKeyException`, which the service catches to fire an "Invalid API Key" notification and stop tracking. This is the only exception that escapes the repository; every other failure returns `null`.
- **The destination is sent as a free-text address, not coordinates.** `SetupActivity.validateAndSaveInputs()` saves `lat/lng` as `0.0`, and the service calls `getEtaMinutesByName(...)`. `getEtaMinutes(lat, lng, …)` exists but is currently unused, as are `AppPreferences.getDestinationLat/Lng`.
- **ETA trend state is deliberately in-memory only.** `EtaForegroundService.lastPollEta` supplies `EXTRA_PREV_ETA`; it is never persisted, and `StatusActivity` passes `-1` on resume so no stale cross-session trend is shown. Do not "fix" this by moving it into `AppPreferences` — a prior fix explicitly removed `KEY_PREV_ETA` (see FIXES.md § FIXES 4).
- **Duration expiry has three independent guards** and all three must keep working: the `isDurationExpired()` check at the top of the polling loop, the same check on each early-return path in `performPoll()` (a failed location fix or API call used to `return` past the evaluator and track forever), and the `scheduleDurationStop()` coroutine that fires mid-interval at the exact expiry moment.
- **Any early `return` you add to `performPoll()` must first check duration expiry** and update the tracking notification, or you reintroduce that bug.
- The service is `START_STICKY` and `onDestroy()` writes `saveTracking(false)`, so a system restart of the service does not resume tracking on its own — `EtaWorker` is what revives a live session.
- `LocalBroadcastManager` is deprecated but is the established IPC path between service and `StatusActivity`; keep using it rather than mixing in a second mechanism.
- API-key validation logic is duplicated in `ApiKeyActivity.validateApiKey()` and `SetupActivity.validateApiKey()`; change both, or extract it.
- All user-facing text lives in `res/values/strings.xml` — a few strings are still hardcoded in `SetupActivity`/`ApiKeyActivity`; prefer adding a resource.

## Conventions

- Kotlin sources live under `app/src/main/kotlin/` (not `java/`), package `com.etaalert`.
- Networking is OkHttp + Gson `JsonParser` with hand-written JSON string bodies; repositories wrap calls in `withContext(Dispatchers.IO)` and swallow exceptions into `null` / `emptyList()`.
- `FIXES.md` is the running log of user-reported bugs and what was changed for each. It is the record of *why* the non-obvious behaviors above exist — read it before changing polling, duration, or trend logic, and append a new section when landing a fix.
