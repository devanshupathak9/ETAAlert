# Smart ETA Alert

An Android app that monitors real-time traffic and notifies the user when it's the optimal time to leave for a destination.

---

## What It Does

Instead of repeatedly checking Google Maps, the user sets a destination, an ETA threshold, and a tracking window. The app polls traffic conditions in the background and fires a one-shot push notification the moment the ETA drops below the target. Tracking then stops automatically.

---

## Core Features

- **Destination selection** — user picks destination; current location is auto-detected
- **Threshold alert** — notify when ETA drops below X minutes (e.g. "alert me when under 18 min")
- **Time-bound tracking** — auto-stops after a user-defined duration (e.g. 1 hour)
- **Periodic polling** — checks every 3–5 minutes via Google Directions API
- **One-shot notification** — fires once when condition is met, then tracking ends
- **Status indicator** — live 🟢 (good to go) / 🔴 (not yet) display in app
- **User-provided API key** — user pastes their own Google Maps API key; no backend needed

---

## How It Works

1. User opens the app and enters: destination, ETA threshold (minutes), tracking duration
2. App starts a **Foreground Service** with a persistent "Tracking ETA..." notification
3. Every 3–5 minutes, the service:
   - Gets current location via `FusedLocationProvider`
   - Calls Google Directions API with `departure_time=now` for live traffic ETA
   - Compares result against user threshold
4. If `ETA ≤ threshold` → push notification fires → service stops
5. If tracking duration expires before condition is met → service stops silently
6. **WorkManager** runs as a heartbeat fallback to restart the service if the OS kills it

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| Background execution | Android Foreground Service + WorkManager |
| Location | FusedLocationProviderClient (Google Play Services) |
| Traffic data | Google Directions API (`departure_time=now`) |
| Local storage | SharedPreferences (API key, user settings) |
| Notifications | NotificationManager (Android standard) |
| Min SDK | Android 8.0 (API 26) |

---

## Architecture

```
UI Layer
  ├── SetupActivity       — destination, threshold, duration input
  ├── StatusFragment      — live ETA, green/red indicator
  └── ApiKeyScreen        — one-time Google Maps API key entry

Service Layer
  ├── EtaForegroundService   — main polling loop, holds wake lock
  └── EtaWorker (WorkManager) — fallback restarter

API Layer
  ├── DirectionsRepository   — wraps Google Directions API calls
  └── LocationRepository     — wraps FusedLocationProvider

Decision Layer
  └── EtaEvaluator          — compares ETA vs threshold, checks duration expiry
```

---

## Google Maps API Setup

Users provide their own API key. Required API to enable in Google Cloud Console:

- **Directions API** — for traffic-aware ETA

Key is stored locally in `SharedPreferences`. All requests go device → Google directly. No backend or server involved.

Free tier: $200/month credit ≈ 40,000 Directions API calls. More than sufficient for personal use.

---

## Known Limitations (MVP)

- Manual API key setup required (friction for non-technical users)
- Background reliability varies by device — aggressive OEMs (Xiaomi, Samsung, Realme) may kill the service
  - Mitigation: guide users to enable autostart permission; link to [dontkillmyapp.com](https://dontkillmyapp.com)
- No route alternatives — single best route only
- No history or commute presets

---

## Permissions Required

```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

---

## Future Enhancements

- Daily commute presets (save frequent routes)
- Predictive "best departure time" based on historical patterns
- Route alternatives comparison
- Home screen widget
- Backend API key management (remove user setup friction)