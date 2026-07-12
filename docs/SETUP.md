# StepBuddy — Setup Notes

This covers the three things a fresh clone needs to be fully live: the Firebase
project, the `google-services.json` config, and hosting the App Link files
(`assetlinks.json` + the `/pair` fallback page).

Everything else (Room cache, sample guides, TTS, notification playback) works
out of the box with no backend — the app degrades gracefully when Firebase is
not yet configured.

---

## 1. Firebase project (free "Spark" tier is enough)

1. Create a project at <https://console.firebase.google.com>.
2. **Add an Android app** with package name **`app.stepbuddy`**.
   - You'll be asked for a debug signing SHA-1/SHA-256 — optional for now, but
     add it later for App Links (see §3).
3. Enable these products:
   - **Authentication → Sign-in method → Anonymous → Enable.**
   - **Firestore Database → Create database** (production mode).
   - **Storage → Get started.**
4. Publish the security rules from this repo:
   - Firestore: paste `firebase/firestore.rules` into **Firestore → Rules**.
   - Storage: paste `firebase/storage.rules` into **Storage → Rules**.
   - (Or with the CLI: `firebase deploy --only firestore:rules,storage`.)

### Firestore indexes
The elderly device queries `guides` with `whereIn("authorId", …)`. Single-field
`whereIn` needs no composite index. If the console ever prompts for one, follow
its one-click link.

---

## 2. The `google-services.json` config file

The repo ships a clearly-labelled **SAMPLE** at `app/google-services.json` so a
fresh clone still configures and builds. Replace it with your real file:

1. Firebase console → **Project settings** → **Your apps** → the Android app →
   **Download google-services.json**.
2. Drop it in at `app/google-services.json` (overwrite the sample).
3. Rebuild. The Google Services Gradle plugin wires it in automatically.

> Tip: in a real project, add `app/google-services.json` to `.gitignore` and
> distribute it out-of-band. It's committed here only to keep the sample
> self-documenting.

---

## 3. Invite links (App Links): host `assetlinks.json` + the `/pair` page

The invite link is an **Android App Link**:
`https://stepbuddy.app/pair?code=123456`.

You need to serve two things from the `stepbuddy.app` domain (any static host —
GitHub Pages, Firebase Hosting, Netlify, Cloudflare Pages):

| URL | File in this repo |
| --- | --- |
| `https://stepbuddy.app/.well-known/assetlinks.json` | `web/.well-known/assetlinks.json` |
| `https://stepbuddy.app/pair` (and `/pair?code=…`)   | `web/pair/index.html` |

### Fill in the fingerprint
Edit `assetlinks.json` and replace the placeholder SHA-256 with your app's
**signing certificate** fingerprint:

- **Release:** Play Console → *App integrity* → *App signing key certificate* →
  copy the SHA-256.
- **Debug (for local testing):**
  ```
  keytool -list -v -keystore ~/.android/debug.keystore \
    -alias androiddebugkey -storepass android -keypass android
  ```
  Add that SHA-256 as an extra entry in the `sha256_cert_fingerprints` array.

### Verify
- Reinstall the app (verification runs at install time), then:
  ```
  adb shell pm get-app-links app.stepbuddy
  ```
  You want `stepbuddy.app: verified`.
- Test the flow: `adb shell am start -a android.intent.action.VIEW -d "https://stepbuddy.app/pair?code=123456"`
  should open StepBuddy on the pre-filled pairing screen.

### If you use a different domain
Change it in **one** place — the `pairingHost` value in `app/build.gradle.kts`
(`manifestPlaceholders`) — and in `PairingRepository.INVITE_HOST`. Then re-host
the two files under the new domain.

### Deferred deep linking (optional)
"Install-then-auto-pair" (the code fills itself in *after* a fresh install) is
intentionally **not** implemented, because Firebase Dynamic Links shut down in
Aug 2025. If you want it, add a third-party service such as **Branch**; the
fallback page already shows the code big so the manual path is one field.

---

## 4. Build & run

- Open the project root in **Android Studio** (Ladybug or newer) and let it sync.
- Or CLI: `./gradlew :app:assembleDebug` (Gradle wrapper jar may need
  `gradle wrapper` once if your checkout lacks it).
- Min SDK 26, target SDK 35.

---

## 5. Privacy checklist (by design)

StepBuddy never captures, stores, autofills, or transmits credentials, OTPs,
live screen content, or payment data. Only caregiver-authored guide content
(text + chosen screenshots) syncs. The authoring screen warns caregivers not to
include account/card/IC numbers in screenshots.
