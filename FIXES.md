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