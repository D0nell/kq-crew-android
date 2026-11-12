# Crew App — Manual Test Sheet

Person performs each step on the Android emulator (API reachable at http://10.0.2.2:8000/).
`Actual` and `Pass/Fail` are left blank for the student to fill in after running each step.

| ID | What to do | Expected result | Actual | Pass/Fail |
|----|------------|-----------------|--------|-----------|
| MT-01 | On the sign-in screen, leave the email empty and the password empty, then look at the Sign in button. | Sign in button is disabled. | | |
| MT-02 | Fill the email field and the password field, then look at the Sign in button. | Sign in button is enabled. | | |
| MT-03 | Sign in with a valid email and a wrong password. | A message says "Invalid email or password." | | |
| MT-04 | Stop the API (stop the uvicorn process), then on the sign-in screen tap Sign in. | A message says "Cannot reach the server." and the app does not crash. | | |
| MT-05 | Sign in with a dispatcher account. | A message says the app is for crew accounts, and the account is signed out. | | |
| MT-06 | On the sign-in screen, tap "Continue with Google", then cancel the account picker. | A message says "Google sign-in was cancelled, or no Google account is set up on this device." and the app does not crash. | | |
| MT-07 | Sign in as a crew member that has transits. For each transit card, verify: pickup time; corridor; "pickup to dropoff"; vehicle and flight; a delay-risk label in text AND colour; and an "Alert" marker when open_alert is true. | Each card shows all of the above. | | |
| MT-08 | Sign in as a crew member that has no transits. | A message says "No transits are assigned to you yet." | | |
| MT-09 | On the transits screen, tap Refresh. | The list reloads (cards update). | | |
| MT-10 | Make a refresh fail (e.g. stop the API or make the API return an error), then tap Refresh. | An error message is shown and the old transit list stays on screen. | | |
| MT-11 | Sign in, then tap Sign out. | Returned to the sign-in screen; POST /logout was called. | | |
| MT-12 | After sign-in, stop the API or make the session stale, then open the transits screen. | A message says "Your session ended. Please sign in again." and the app returns to the sign-in screen. | | |
| MT-13 | Sign in as crew.demo@example.com. | There are 11 transit cards and the colour of each delay-risk label is correct for its value. | | |
| MT-14 | Sign in, then stop the API. | A message says "Cannot reach the server.", with no crash and no stale screen. | | |
| MT-15 | Sign out, confirm the sign-in screen is shown, then press the emulator Back button. | The sign-in screen remains and no old transit data is shown. | | |
