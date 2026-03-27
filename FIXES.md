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