# App Store (iPhone): «سهرة وتسالي»

Images in this folder: `phone-01…06.png` (1290×2796, the 6.7"/6.9" iPhone slot). The app is **iPhone-only** (no iPad
screenshots needed). Icon: comes from the app build itself (1024 in the asset catalog).

## A. One-time setup (owner, in your Apple Developer / App Store Connect account, same as PULSE)
1. **developer.apple.com → Identifiers → +** → App IDs → App → Bundle ID `online.geddo.sahrah` (explicit). Capabilities:
   tick **Sign in with Apple** (see C) and **Push Notifications** (for later).
2. **App Store Connect → Apps → + → New App**: platform iOS, name «سهرة وتسالي», primary language Arabic, bundle ID
   `online.geddo.sahrah`, SKU `sahrah`, full access. Then copy **App Information → Apple ID** (a number) and send it to me:
   it goes into `APP_APPLE_ID` in `codemagic.yaml`.
3. Codemagic already has the team's `appstore-key` (PULSE): nothing new. I push the tag `ios-v1.0.0` → the build goes to
   **TestFlight** by itself. Install «TestFlight» on your iPhone to try it before submitting.
4. **AdMob (iPhone ads)**: in AdMob add an **iOS app** «سهرة وتسالي» and send me its App ID (+ an interstitial and a
   rewarded unit). Until then the iPhone app shows no ads.

## B. App Store listing (copy/paste)

**Name** (30): سهرة وتسالي
**Subtitle** (30): أسئلة وضحك وتحدي مع صحابك
**Promotional text** (170, can change any time without review):
العبوا فرق ضد فرق أو ١ ضد ١ مع صحابك: أكتر من ١٩٠ فئة مصرية، صور وإيموجي وخريطة، وتحدي كل يوم.
**Description** (4000): use the same text as Google Play (`../android/LISTING.md` → Full description).
**Keywords** (100, comma-separated, no spaces after commas):
مسابقات,أسئلة,لعبة جماعية,فرق,تحدي,مصري,ثقافة عامة,كورة,أفلام,سهرة,trivia,quiz,party
**Support URL**: https://sahrah.geddo.online/account  (has «كلمنا»)
**Marketing URL**: https://sahrah.geddo.online
**Privacy Policy URL**: https://sahrah.geddo.online/privacy
**Category**: Games → Trivia (secondary: Games → Family)
**Copyright**: 2026 Geddo
**Age rating questionnaire**: everything "None", except:
- *Unrestricted web access*: **No**. The app only opens our own site.
- *User-generated content / messaging*: **Yes**. Chat is filtered (numbers, links and accounts are blocked) and has report and
  block. The result is about 12+.
- *Gambling / contests*: **No** (no money, no betting).

**App Privacy (nutrition labels)**:
- Contact Info → Name, Email Address: *Linked to the user*, purpose *App Functionality* (only when the player signs in).
- User Content → Other User Content (chat): *Linked*, *App Functionality*.
- Identifiers → Device ID: *Not linked*, *Analytics* + *App Functionality*.
- Usage Data → Product Interaction: *Not linked*, *Analytics*.
- When iPhone ads (AdMob) are on, add: Identifiers → Advertising Data/Device ID *Third-party advertising*, and answer
  "Yes, used for tracking" only if we show the ATT prompt (we won't at first: non-personalised ads).
- **Account deletion**: in the app («حسابي» → «امسح حسابي») + https://sahrah.geddo.online/delete-account

## C. Things Apple checks that Google doesn't
1. **Sign in with Apple (guideline 4.8)**: apps that offer Google/Facebook sign-in must also offer «Sign in with Apple».
   → I add an «كمّل بآبل» button (web + app) before the first iPhone submission. **Needed from you**:
   - developer.apple.com → Identifiers → **Services IDs → +** → id `online.geddo.sahrah.web`, enable Sign in with Apple,
     domain `sahrah.geddo.online`, return URL `https://sahrah.geddo.online/api/auth/apple/callback`;
   - Keys → **+** → enable Sign in with Apple → download the `.p8` key (put it on the laptop for me; I never print it).
2. **"Not just a website" (4.2)**: the app has a native splash, icon, offline screen, status bar and app links. If the
   reviewer still says "web wrapper", the answer is the **review notes** below. Push notifications (native) are the next
   step if they insist.
3. **Payments (3.1.1)**: nothing is sold in the app (subscriptions stay hidden; later only via Apple In-App Purchase).
4. **Export compliance**: already answered in the app («uses only standard HTTPS»), so no questionnaire on upload.

## D. App Review notes (paste in "App Review Information → Notes")
سهرة وتسالي is an Egyptian party trivia game. No login is needed: open the app and tap «العب» to start a team game on one
phone, or «١ ضد ١» to play a friend. Optional sign-in (Google/Facebook/Apple) saves games and enables friends and chat.
Chat is filtered (phone numbers, links and accounts are blocked) and every chat has report and block.
Content is our own (questions written for Egyptian players); the app is the native client of our service at
sahrah.geddo.online, with offline handling and native navigation.
Demo: no special account needed.

## E. Release
1. TestFlight build arrives → test on your iPhone.
2. App Store Connect → the version → add the TestFlight build, screenshots, texts → **Submit for Review**.
3. Review usually takes 1–3 days. If rejected, send me Apple's message: I fix and we resubmit.
