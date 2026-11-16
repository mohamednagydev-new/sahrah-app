# Release 2.1 (Android): links open in the app, Firebase Analytics for Google Ads

1. **App Links** (owner 16 Nov 2026: a شلة invite from WhatsApp opened the browser, not the app): the manifest's App Links list
   also covers /s/ (شلة invites), /clubs, /friends, /shella, /masr, /dawry, /prizes, /how-to-play and /dorak («دورك يا صاحبي»).
   The site already sends شلة / دورك links through «/» (/?s=, /?dorak=) so older versions open them too (seenjeem-egypt SC10).
2. **Firebase Analytics** (owner 16 Nov 2026, RT5: «yes add to it»): `com.google.firebase:firebase-analytics:23.0.0`, same Firebase
   project as push (google-services.json). `SahrahNativePlugin.logEvent({name})` sends ONE allowed event, `first_game_done`, with
   no parameters (the page sends it once per phone after its first finished game, lib/appEvents.ts). Google Ads imports it from
   GA4 as a conversion for an App campaign optimised for in-app actions. Firebase also logs its automatic events (first_open,
   session_start, app updates) — **Play Console «Data safety» must declare App activity + Device or other IDs (analytics)**.
3. Version: versionName 2.1, user agent `SahrahApp/2.1` (the page sends the event only from 2.1).

Not compiled locally (Codemagic builds it). Check on a phone: first finished game → Firebase DebugView / GA4 realtime shows
first_game_done; a شلة link from WhatsApp opens the app.
