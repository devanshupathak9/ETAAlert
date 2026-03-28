# UI Fixes and Improvements

## FIXES 1:
### 1. Destination Input Box
- The destination input box in the UI is currently too small and not properly styled.
- Improve its size, spacing, and overall layout for better usability and visibility.

### 2. Default Landing Page
- When the app is opened, it should land on the main page by default.
- The main page should include:
  - Destination input field
  - Duration selection dropdown
  - Option to add or update the API key

### 3. Add / Update API Key Page
- The API key page should include:
  - Clear instructions on how to create an API key
  - Input field to enter/update the API key
  - A back button to navigate to the main page

### 4. API Key Validation
- After entering the API key:
  - The system should validate the key
  - Display a success message if the key is valid
  - Display an error message if the key is invalid

### 5. Duration Dropdown خيارات
- The duration selection should be a dropdown with fixed options:
  - 30 minutes
  - 60 minutes
  - 90 minutes
  - 120 minutes

### 6. Search Behavior
- When the user clicks the "Search" button:
  - First, verify whether the API key is valid
  - If the API key is valid:
    - Proceed with the search
  - If the API key is invalid:
    - Show an error message
    - Do not proceed with the search

---
## FIXES 2:
What was broken and what's fixed                                                                                                  
                                                                                                                                    
Bug 1 — Duration never stopped tracking                                                                                           
The real bug: when getCurrentLocation() or the Directions API call failed, performPoll() hit a return before ever reaching the    
duration check in EtaEvaluator. So if you had bad GPS signal or intermittent network, the service would just keep retrying        
indefinitely, ignoring the 30-min limit entirely.
                                                                                                                                  
Fix: added an isDurationExpired() helper and call it on both early-return paths. Now even failed polls respect the duration.      
  
Bug 2 — Same ETA time showing                                                                                                     
This was a symptom of the above — if polls were silently failing (no location / API error), the UI kept showing the last cached
ETA from SharedPreferences with no indication anything was happening.                                                             
                
Fix: the notification now shows "Poll #N — Could not get location. Retrying..." so you can see it's alive even when the           
API/location fails.
                                                                                                                                  
Feature — Visible poll counter                                                                                                    
- Added Poll #1, Poll #2... to both the persistent notification text AND a new label in StatusActivity
- Poll count resets to 0 when you start a new tracking session                                                                    
- If you open the app mid-tracking, it reads the saved poll count from SharedPreferences to show the current count immediately
                                                                                                                                  
Bonus — Auto-navigate back when tracking stops                                                                                    
Added ACTION_TRACKING_STOPPED broadcast. When the service stops (duration expired or alert fired), if the app is open it now      
automatically navigates back to the Setup screen instead of sitting frozen on the Status screen.


## FIXES 3:
Search Destination Input Box
Add padding to the search destination input field so that the text does not start right at the left boundary.
Tracking Duration Option
Include a 15-minute tracking duration option in the list of available durations.
Stop Tracking After Duration Ends
Ensure that tracking automatically stops once the selected duration is completed. It should not continue or extend beyond the set time.

Polling Behavior During Tracking
Once tracking starts for the selected duration, the system should perform an ETA check every 3 minutes throughout that duration.

Each API call made during this interval should be labeled sequentially (e.g., Poll 1, Poll 2, Poll 3, etc.). This labeling should be visible in the app so users can clearly see that periodic checks are happening.

This serves as confirmation that:

The app is actively running tracking in the background
API calls are being executed at the expected intervals
The tracking functionality is working reliably during the entire duration

Polling should continue only within the selected tracking duration and must stop once the duration ends.

Hard Stop After Duration
Implement a strict hard stop when the tracking duration ends.
No further polling or tracking actions should occur after this point.

ETA-Based Notifications and Visual Indicators
When the ETA falls below a defined threshold, trigger a strong notification (e.g., sound alert) to immediately notify the user.

Additionally, track changes in ETA across each poll:

If the ETA decreases compared to the previous value, highlight this with a distinct visual indicator (e.g., a different color) to show improvement.
Maintain a comparison with the previous poll’s ETA to determine whether it is increasing or decreasing.

This ensures users are clearly informed about meaningful changes in ETA and can easily track progress over time.
                                                                                                                                                                                                                             
1. Destination input padding — Added paddingStart="16dp" and paddingEnd="16dp" to the MaterialAutoCompleteTextView so text no longer starts at the left boundary.                                                                     
2. 15-minute tracking duration — Added 15 to durationOptions list in SetupActivity and a matching string resource.                                                                                                                    
3. Polling every 3 minutes — Changed POLL_INTERVAL_MS from 4 * 60 * 1000L to 3 * 60 * 1000L.                                                                                                                                           
4. Hard stop after duration — Two mechanisms:                                                                                                                                                                                         
- The polling loop now checks isDurationExpired() before each poll and breaks if expired.
- A new scheduleDurationStop() coroutine fires at the exact moment the duration ends, cancelling the polling job regardless of where in a 3-minute interval the service is.                                                                                                                                                                       
5. Poll labels in UI — The tvPollCount now shows "Poll N — checking every 3 min" so users can clearly see sequential poll counts and know the interval.                                                                                                                                                                                                                           
6. ETA trend indicator — New tvEtaTrend TextView on the status screen shows:                                                                                                                                                          
- ↓ X min faster than last poll in green when ETA improves                                                                                                                                                                            
- ↑ X min slower than last poll in orange when ETA worsens                                                                                                                                                                            
- → Same as last poll in gray when unchanged                
- Hidden (empty) on the first poll since there's no previous value to compare                                                                                                                                                                                                                        
7. Strong alert notification — Alert channel now uses alarm ringtone (RingtoneManager.TYPE_ALARM), longer vibration pattern (0, 500, 200, 500, 200, 500), and AudioAttributes.USAGE_ALARM for maximum prominence.



## FIXES 4:
Instead of latitude and longitude, it should display your current location.
Even if the destination changes, it still picks up the previous ETA from the last track and compares it with the new one, showing the time difference.
Whenever we start a new tracking session, keep the first ETA as the base and compare subsequent requests against it.
1st ETA: no change (no comparison)
2nd ETA: show increase or decrease compared to the previous one
3rd ETA: compare with the 2nd one
Do not store these values in long-term memory; keep them temporary and remove them after use.
Add a better alerting system when the value goes below a defined threshold.
                                                                                                                                                                                                                                       
  EtaForegroundService.kt                                                                                                                                                                                                              
  - Added private var lastPollEta: Int = -1 — in-memory only, never written to disk                                                                                                                                                    
  - startTracking() resets both lastPollEta and prefs.saveLastEta(-1), so a new session always starts clean with no prior comparison                                                                                                   
  - Each poll: uses lastPollEta as prevEta (not from SharedPreferences), then updates lastPollEta = etaMinutes                                                                                                                         
  - Calls placesRepo.reverseGeocode() after each location fix and broadcasts the address as EXTRA_LOCATION_ADDRESS                                                                                                                     
  - fireAlertNotification() now: PRIORITY_MAX, CATEGORY_ALARM, BigTextStyle, setFullScreenIntent (shows on lock screen), longer vibration pattern (600ms × 4)                                                                          
                                                                                                                                                                                                                                       
  AppPreferences.kt                                                                                                                                                                                                                    
  - Removed KEY_PREV_ETA, savePrevEta(), getPrevEta() — comparison values are no longer persisted at all                                                                                                                               
                                                                                                                                                                                                                                       
  StatusActivity.kt                                         
  - Added tvCurrentLocation (shows 📍 From: <address>) and tvAlertBanner views                                                                                                                                                         
  - Broadcast receiver now reads EXTRA_LOCATION_ADDRESS and updates the location label                                                                                                                                                 
  - On resume/restore: passes -1 as prevEta so no stale cross-session trend is shown  
  - When ETA ≤ threshold: shows a bright green banner ("Leave now! ETA X min is under your Y min threshold") in-app                                                                                                                    
                                                                                                                                                                                                                                       
  activity_status.xml                                                                                                                                                                                                                  
  - Added tvCurrentLocation below destination label                                                                                                                                                                                    
  - Added tvAlertBanner (green background, bold white text, visibility="gone" by default)                                                                                                                                              
                                                            
  strings.xml                                                                                                                                                                                                                          
  - Added status_current_location and alert_banner_leave_now strings