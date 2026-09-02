# ETA Alert — Android App

ETA Alert is an Android application that monitors real-time traffic and notifies you at the exact moment it's time to leave for your destination. Instead of repeatedly checking Google Maps, you set a destination, an ETA threshold, and a tracking window — the app does the rest in the background and fires a single push notification when the drive time drops below your target.

---

## How It Works

1. Enter your destination, an ETA threshold (e.g. "alert me when under 18 min"), and how long to keep tracking (15 / 30 / 60 / 90 / 120 minutes).
2. The app starts a foreground service that holds a wake lock and checks traffic **every 3 minutes**.
3. Each check gets your current location, asks Google for the live traffic-aware drive time, and compares it to your threshold.
4. The moment `ETA ≤ threshold`, an alarm-style notification fires and tracking stops. You get one alert, not a stream of them.
5. If the tracking window runs out first, the service stops silently.
6. A WorkManager heartbeat restarts the service if an aggressive OEM battery manager kills it.

---

## Features

- **Destination autocomplete** — suggestions biased to where you are now
- **Live "From" location** — auto-detected and reverse-geocoded to a readable address
- **One-shot alert** — alarm ringtone, vibration, and full-screen notification when it's time to go
- **Time-bound tracking** — hard stop when the window expires; it will not run forever
- **Status screen** — live ETA, green/red indicator, and whether the ETA is rising or falling since the last check
- **Your own API key** — validated on entry, stored on-device; there is no backend and no account

---

## Requirements

- Android 8.0 (API 26) or newer
- A Google Maps API key (see below) — the app is unusable without one
- Location and notification permissions

---

## Getting a Google Maps API Key

The app makes every request directly from your phone using your own key, so you'll need one before anything works. It takes about ten minutes.

**1. Create a Google Cloud project**

Go to [console.cloud.google.com](https://console.cloud.google.com/), sign in, and click the project dropdown in the top bar → **New Project**. Name it anything (e.g. "ETA Alert") and click **Create**. Make sure the new project is selected in that dropdown before continuing.

**2. Enable billing**

Google requires a billing account on the project even for free-tier usage. Go to **Billing** in the left menu and link a payment method. You will not be charged inside the free tier, but the APIs return errors until billing is attached.

**3. Enable the three APIs the app uses**

Go to **APIs & Services → Library** and search for each of these, opening it and clicking **Enable**:

| API | What it's for |
|---|---|
| **Routes API** | the traffic-aware drive time — the core feature |
| **Places API** | destination autocomplete suggestions |
| **Geocoding API** | turning your coordinates into a readable address |

All three are required. Miss one and that feature silently stops working — most commonly, autocomplete returns nothing.

> **Watch out:** enable **Places API**, not "Places API (New)". The app calls the classic autocomplete endpoint, and the new API won't answer it.

**4. Create the key**

Go to **APIs & Services → Credentials → Create Credentials → API key**. Copy the key that appears — it starts with `AIza`.

**5. Restrict it (recommended)**

On the key's edit page, under **API restrictions**, choose **Restrict key** and tick only the three APIs above. Leave **Application restrictions** set to *None* — Android app restriction requires a signing-certificate fingerprint that debug builds don't have a stable one for, and it will reject your requests.

**6. Paste it into the app**

Open ETA Alert → **Add / Update API Key**, paste, and tap verify. The app makes a real test request, so a green confirmation means the key genuinely works. If you see an error, it's almost always step 2 or step 3.

Costs: Google's free monthly usage is generous relative to personal use — a tracking session is roughly 20 requests per hour — but the per-API allowances change, so check [current pricing](https://developers.google.com/maps/billing-and-pricing/pricing) if you plan to run it heavily. You can also set a budget alert under **Billing → Budgets & alerts**.

---

## Build & Run

Requires Android Studio, a JDK, and the Android SDK.

```bash
./gradlew assembleDebug     # build the APK
./gradlew installDebug      # build and install on a connected device
```

Or open the project in Android Studio, let Gradle sync, and hit Run.

---

## Tech Stack

Kotlin · Foreground Service + WorkManager · FusedLocationProvider · Google Routes / Places / Geocoding APIs · OkHttp + Gson · SharedPreferences · minSdk 26

See `FIXES.md` for the log of reported issues and what changed for each.
