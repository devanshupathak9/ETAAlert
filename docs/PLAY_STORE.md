# Google Play submission — copy/paste sheet

Everything below is meant to be pasted straight into Play Console. Two things
still need doing by hand: turning on GitHub Pages, and making the graphics.

---

## 0. Before you open Play Console

### Turn on GitHub Pages (gets you the privacy policy URL)

1. Push this repo.
2. GitHub → your repo → **Settings** → **Pages**.
3. Source: **Deploy from a branch**. Branch: `master` (or `main`), folder: **`/docs`**.
4. **Save.** Wait ~1 minute.
5. Your privacy policy URL is:

   ```
   https://devanshupathak9.github.io/ETAAlert/
   ```

   Open it and confirm it loads before continuing. Play Console rejects a URL that 404s.

### Build the release AAB

Play Store needs an `.aab`, not an `.apk`.

Android Studio → **Build** → **Generate Signed Bundle / APK** → **Android App Bundle** →
create a new keystore → remember the password → Release.

> **Keep the keystore file and its password forever.** Lose it and you can never
> update this app again — you would have to publish it as a brand-new listing.

---

## 1. Data safety form

Play Console → **Policy** → **App content** → **Data safety**.

### Overview questions

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes** |
| Is all of the user data collected by your app encrypted in transit? | **Yes** (all Google Maps calls are HTTPS) |
| Do you provide a way for users to request that their data is deleted? | **No** — and add the note below |

Note to paste if it asks for an explanation:

> The app has no server and no account. All data is stored locally on the device
> and is deleted when the user uninstalls the app or clears app data.

### Data types — tick ONLY these

**Location → Approximate location**
**Location → Precise location**

For **both** of them, answer:

| Question | Answer |
|---|---|
| Collected | **Yes** |
| Shared | **No** |
| Processed ephemerally | **Yes** — used for a single request, never written to storage |
| Required or optional | **Required** |
| Purpose | **App functionality** (tick this only) |

Do **not** tick: Personal info, Financial info, Health, Messages, Photos, Files,
Contacts, Calendar, App activity, Web browsing, App info and performance, Device IDs.

> The API key is user-supplied credentials for the user's *own* Google Cloud
> account, kept on-device and never transmitted to the developer. It is not a
> Play "data type" and does not get declared here.

---

## 2. Location permissions declaration

Play Console → **Policy** → **App content** → **Sensitive app permissions** →
**Location permissions**.

| Question | Answer |
|---|---|
| Does your app access location in the background? | **No** — the app uses a foreground service, not `ACCESS_BACKGROUND_LOCATION` |
| Core functionality that uses location | Continuously recalculating drive time to the destination the user entered |

Paste as the feature description:

> ETA Alert repeatedly calculates the live traffic drive time between the user's
> current position and a destination they entered, then sends a single
> notification the moment that drive time drops below a threshold they set. The
> user's location is the origin point of that calculation, so the feature cannot
> work without it. Location access runs inside a foreground service with an
> ongoing, always-visible notification, and stops automatically when the user is
> alerted, when their chosen tracking window expires, or when they press Stop.

If it asks where the prominent disclosure is shown:

> A full-screen disclosure dialog appears on the setup screen immediately before
> the system location permission prompt. It states that location is collected
> including while the app is not in use, explains that it is sent directly to
> Google Maps to calculate drive time and is never sent to the developer, and
> requires the user to tap "Allow location" to proceed. Tapping "Not now"
> cancels without requesting the permission.

---

## 3. Store listing

Play Console → **Grow** → **Main store listing**.

### App name (30 characters max)

```
ETA Alert
```

### Short description (80 characters max)

```
Get one alert the moment traffic clears and it's time to leave. Stop refreshing.
```

*(79 characters)*

### Full description (4000 characters max)

```
Stop opening Google Maps every ten minutes to see whether the traffic has cleared.

ETA Alert watches it for you. Tell it where you're going, how long the drive
needs to get down to, and how long to keep watching. Then put your phone down.
The moment live traffic drops your drive time below your target, you get one
loud notification — and tracking stops on its own.

HOW IT WORKS

1. Enter your destination.
2. Set your alert threshold, for example "tell me when it's under 20 minutes".
3. Choose how long to keep watching — 15 minutes up to 2 hours.
4. Press Start and carry on with your day.
5. Get a single alarm-style alert when it's time to leave.

WHY IT'S DIFFERENT

• One alert, not a stream of them. It fires once and stops.
• Works in the background with an ongoing notification, so you always know
  when it's running.
• Stops by itself — when you're alerted, or when your tracking window ends.
  It never quietly keeps running and draining your battery.
• Live ETA and trend are visible on the status screen while it works.

PRIVACY

No accounts. No servers. No ads. No analytics.

Your location goes directly from your phone to Google Maps and nowhere else.
The developer never receives it. Your settings live only on your device and are
gone the moment you uninstall.

SETUP NOTE — PLEASE READ

ETA Alert uses your own Google Maps API key rather than the developer's, which
is why the app is free and collects nothing. Before first use you'll need to
create a key at console.cloud.google.com and enable the Routes API, Places API,
and Geocoding API on it. The app walks you through this on the first screen.
Google's free monthly Maps credit comfortably covers normal personal use.
```

### App category

- **App category:** Maps & Navigation
- **Tags:** navigation, traffic, commute

### Contact details

- **Email:** your email address
- **Website:** `https://github.com/devanshupathak9/ETAAlert`
- **Privacy policy:** `https://devanshupathak9.github.io/ETAAlert/`

---

## 4. Graphics you still have to make

| Asset | Size | How |
|---|---|---|
| App icon | 512 × 512 PNG | Android Studio → right-click `res` → New → Image Asset, or export the existing launcher icon |
| Feature graphic | 1024 × 500 PNG | Canva has free templates — app name on a solid `#1976D2` background is enough |
| Phone screenshots | at least 2, min 1080px wide | Run the app on your phone and take real screenshots. Best ones: the setup screen with a destination filled in, and the status screen showing a live ETA |

---

## 5. Content rating & other App content sections

Play Console → **Policy** → **App content**. Work down the list:

- **Content rating questionnaire** — answer No to everything. You will get rated
  Everyone / PEGI 3.
- **Target audience** — 18+ (or 13+). Do **not** select a children's audience;
  it triggers extra requirements.
- **Ads** — **No, my app does not contain ads.**
- **News app** — No.
- **COVID-19 apps** — No.
- **Data safety** — done in section 1 above.
- **Government apps** — No.
- **Financial features** — None.
- **Health** — No.

---

## 6. Release track

Start with **Testing → Internal testing**, not Production.

- Live within minutes, no review queue.
- Up to 100 testers, added by email address.
- Skips the "12 testers for 14 consecutive days" rule that applies to new
  personal developer accounts before a production release.
- You still need the privacy policy URL and the Data safety form filled in.

Play Console → **Testing** → **Internal testing** → **Create new release** →
upload the `.aab` → add tester emails → copy the opt-in link and send it out.

Move to Production later, once the app has been used on real phones for a while.
