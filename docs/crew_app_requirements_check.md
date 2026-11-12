# Crew App Requirements Check (R1–R10)

Review of `C:/Users/donel/AndroidStudioProjects/KQCrew` (read only, no changes).
Status: PASS for every requirement.

| ID | Requirement | Status | File | Function / proof |
|----|-------------|--------|------|------------------|
| R1 | Sign in with email and password; the button is disabled until both fields are filled. | PASS | MainActivity.kt | LoginScreen: `Button enabled = !busy && email.isNotBlank() && password.isNotEmpty()` |
| R2 | A wrong password shows "Invalid email or password." | PASS | MainActivity.kt | `signIn`: `if (login.code() == 401) "Invalid email or password."` (backend `/login` returns 401 for wrong creds) |
| R3 | Server down or no network shows "Cannot reach the server". | PASS | MainActivity.kt | `signIn`, `googleSignIn`, `refresh`: catch `Exception` -> "Cannot reach the server. Check that the API is running." |
| R4 | A DISPATCHER account is refused with a message saying the app is for crew accounts, and is signed out of the server. | PASS | MainActivity.kt | `finishSignIn`: `if (role != "CREW") { KqClient.api.logout(); message = "This app is for crew accounts. Dispatchers use the web dashboard." }` |
| R5 | Continue with Google signs in with the account on the phone. Cancelling shows a clear message and does not crash. | PASS | MainActivity.kt | `googleSignIn`: `GetGoogleIdOption` + `CredentialManager`; `catch (e: GetCredentialException) -> "Google sign-in was cancelled, or no Google account is set up on this device."` |
| R6 | My transits shows one card per transit: pickup time, corridor, pickup to dropoff, vehicle and flight, a delay-risk label in text AND colour (LOW, MEDIUM, HIGH, CRITICAL, or not scored), and an Alert marker when open_alert is true. | PASS | MainActivity.kt | `TransitCard`: `formatTime`, `t.corridor`, `pickup to dropoff`, `vehicle | Flight to destination`, `riskLabel(risk_level)` with `riskColor(risk_level)`, `if (t.open_alert) "Alert"` |
| R7 | An empty list shows "No transits are assigned to you yet." | PASS | MainActivity.kt | `TransitScreen`: `if (transits.isEmpty()) Text("No transits are assigned to you yet.")`) |
| R8 | Refresh reloads the list. A failed refresh shows an error and keeps the old list. | PASS | MainActivity.kt | `refresh`: on failure `message = "Could not refresh (HTTP ${list.code()})."` and `transits` is unchanged |
| R9 | Sign out calls POST /logout and returns to the sign-in screen, even if the server cannot be reached. | PASS | MainActivity.kt | `signOut`: `try { KqClient.api.logout() } catch (e: Exception) { /* local sign-out enough */ }`; then `signedInName=null`, `transits=emptyList()` |
| R10 | If the server answers 401 (session expired), the app returns to the sign-in screen with a clear message. | PASS | MainActivity.kt | `refresh`: `if (list.code() == 401) { signedInName = null; message = "Your session ended. Please sign in again." }` |

Notes:
- Backend `/api/my-transits` returns fields matching the app's `Transit` data class
  (`corridor`, `pickup_location`, `dropoff_location`, `scheduled_pickup_time`,
  `vehicle_type`, `flight_number`, `destination`, `risk_level`, `open_alert`), so the card
  content is fully populated.
- `riskLabel` null->"Not scored yet"; `riskColor` LOW/MEDIUM/HIGH/CRITICAL+else.
  No gap found: every requirement is implemented.
