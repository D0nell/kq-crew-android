# Crew App Summary

KQ Crew is a Jetpack Compose Android app so crew members can see their assigned transit
cards and delay-risk alerts. It signs in through a cookie-based API at
http://10.0.2.2:8000/. Password sign-in sends email and password to POST /login; Google
sign-in sends an ID token to POST /auth/google; both issue a kq_session cookie. After
sign-in the app checks the role, calls GET /api/me, and shows GET /api/my-transits as one
TransitCard per transit with a delay-risk label in text and colour and an Alert marker
when open_alert is true. It supports refresh and sign-out.

Automatic tests: the backend pytest suite (98 checks) and the Android unit tests in
CrewAppLogicTest.kt (formatTime, riskLabel, riskColor). Everything else is tested by hand
via the manual test sheet MT-01 to MT-15 on the emulator.

Future work: push notifications, offline cache, live GPS tracking, and live weather and
traffic data.
