# Release 1.9 (Android): safer links, the offline screen retries by itself

Small release on top of 1.8 (13 Nov 2026, from the security and reliability reviews in seenjeem-egypt docs/review/).
NOT run on a phone before the tag: the internal-testing check below is the test.

What 1.9 changes, compared with 1.8:
1. **The native bridge only for the app's own site** (`capacitor.config.json` `allowNavigation` = `sahrah.geddo.online` only; it
   used to list `*.geddo.online`, the rooms host and the LiveKit host, none of which are ever opened as a page).
2. **A link handed to the app never opens `/api` or `/admin`** (`MainActivity.isOurLink`).
3. **The offline screen retries by itself** (`www/offline.html`): when the network is back it reloads the game, no tap needed.
4. User agent `SahrahApp/1.9`, versionName `1.9`, versionCode = max(111, Codemagic BUILD_NUMBER + 100).

Not in 1.9 (planned for the next one): the first launch no longer loading the page twice for the install source; a friend's
invite continuing in the app after install; the sign-in return path (security #19).

## Steps (owner)
1. Codemagic `android-release` builds the `.aab` from the tag `android-v1.9` (check the versionCode in the log is higher than 1.8's).
2. Play Console → Testing → **Internal testing** → Create release → upload the `.aab` → notes below → roll out.
3. On a real phone from the internal track:
   - the app opens and the home shows; play one 1 ضد 1 and one street game (rooms + 3D);
   - **sign in with Google and with Facebook** (opens outside, comes back signed in) — the allow-list change is the risk here;
   - voice in a room (mic popup, hear each other); «فعّل» notifications + a test push; share; an ad shows;
   - an `https://sahrah.geddo.online/...` link (a friend's invite) opens inside the app;
   - airplane mode on → the offline screen; airplane mode off → the game comes back by itself.
4. Only if all pass: Production → Create release → add from library (the same build) → staged rollout 20% → watch Android
   vitals 2 days → 100%.
5. When 1.9 is live on Play, tell the lead to set admin «نسخة التطبيق» (`app.latest`) to 1.9.

## Release notes for Play (ar)
Copy from `RELEASE-NOTES-1.9-ar.txt` (each line starts with an invisible right-to-left mark).
