# StepBuddy 👣

**Caregiver-authored, step-by-step guidance for elderly users.**

StepBuddy helps older people who struggle to *remember* how to do digital tasks
(online bank transfers, e-wallet top-ups, booking appointments). It never does
the task for them. A caregiver pre-records a step-by-step guide remotely; the
app walks the elderly user through each step with **large text** and **spoken
audio** in their language, while **they** do the actual tapping.

Native Android · Kotlin · Jetpack Compose · Material 3 · min SDK 26.

---

## Two roles, one app

Chosen once at first-run onboarding.

### 🧓 Elderly
- No authoring. After a one-time pairing, guides appear and stay up to date on
  their own.
- Big-tile home of received guides.
- Tapping a guide starts guidance: each step shows large text + is **read aloud
  (TTS)**, offers a **“Ready → open the app/page”** button, and keeps a
  **persistent, expandable notification** (“Step 2 of 6”) with big **Repeat /
  Next / Back / Close** actions so guidance survives switching to the bank app.
- Optional floating **overlay bubble** with the same controls, where allowed.

### 👨‍👩‍👧 Caregiver (on their own phone)
- Create guides: title, emoji, language, ordered steps.
- Each step: plain-language instruction, optional screenshot with a
  drag-to-draw **highlight box**, optional **target** (open a URL or launch an
  app by package).
- Reorder / edit / duplicate / delete steps and guides.
- Link elderly phones via **6-digit code + QR** or an **invite link**.
- Saved changes push to linked phones automatically.

---

## How it works (architecture)

```
        Caregiver phone                         Elderly phone
   ┌───────────────────────┐             ┌───────────────────────────┐
   │ Compose UI (author)   │             │ Compose UI (big/spoken)   │
   │        │              │             │      ▲  reads from Room    │
   │        ▼              │             │      │                     │
   │ GuideRepository ──────┼── push ───▶ Firestore ──┐               │
   │   (Room + Firebase)   │   guides    (guides,     │ listeners     │
   │        │              │   +steps    steps,       ▼ (near-instant)│
   │        ▼              │   +images)  pairings, ── SyncManager ──▶ │
   │ Room (local cache)    │             links)       │  writes Room  │
   └───────────────────────┘             └────────────┼──────────────┘
                                                        ▼
                                          GuidePlaybackService (foreground)
                                           ├─ persistent notification (primary)
                                           ├─ TtsManager (per-guide language)
                                           └─ OverlayService (optional, best-effort)
```

- **Single source of truth = Room.** The UI only ever reads Room, so both roles
  work fully **offline** after the first sync. Writes go to Room immediately and
  are mirrored to Firestore.
- **`PlaybackController`** holds the one in-progress playback state; the
  on-screen buttons *and* the notification *and* the overlay all read/write it,
  so they never drift out of sync.
- **Notification is the PRIMARY, always-reliable channel.** Many secure apps use
  `FLAG_SECURE` and block overlays, so the overlay is strictly best-effort and
  falls back silently.

### Package layout (`app/src/main/java/app/stepbuddy/`)
| Package | What's inside |
| --- | --- |
| `data/model` | Plain domain models (Guide, Step, Pairing, Settings, enums). |
| `data/local` | Room entities, DAO, converters, mappers (offline cache). |
| `data/remote` | Firebase Auth + Firestore + Storage gateway (`FirebaseSync`). |
| `data/repository` | `GuideRepository`, `PairingRepository`, `SyncManager`. |
| `data/settings` | DataStore-backed `SettingsRepository`. |
| `data/export` | `.stepbuddy` file export/import (cloud-free fallback). |
| `tts` | `TtsManager` — per-guide language, missing-voice handling. |
| `service` | `GuidePlaybackService` (foreground + notification), `OverlayService`, `PlaybackController`. |
| `ui/*` | Compose screens + ViewModels (onboarding, elderly, caregiver, pairing, settings). |
| `util` | Intent launching, QR generation, image storage. |
| `di` | Tiny manual DI container (no Hilt). |

---

## Pairing & sync

Three paths, all converging on one Firestore pairing document keyed by the
6-digit code:

1. **Type the code** (great when read over a phone call).
2. **Scan the QR** (great when set up in person).
3. **Tap the invite link** — an **App Link**
   `https://stepbuddy.app/pair?code=123456`:
   - App installed → opens straight to pairing with the code **pre-filled**;
     the elderly user taps one big **Connect**.
   - App not installed → the domain serves `web/pair/index.html`: a big
     **Install** button + the code in big digits.

After pairing, guides sync down via **Firestore listeners** (near-instant push),
are cached in **Room**, and access is enforced by **Firestore Security Rules**
(`firebase/firestore.rules`) via a `links/{caregiverId}_{elderlyId}` membership
document. A caregiver can also **export a `.stepbuddy` file** to share manually
when there's no cloud.

See **[docs/SETUP.md](docs/SETUP.md)** for Firebase setup, the config file, and
hosting `assetlinks.json` + the fallback page.

---

## Accessibility (elderly role)

Very large default font (user-adjustable), high-contrast warm-teal theme, ≥56dp
touch targets, one clear action per screen, no jargon, every screen readable
aloud, adjustable speech speed, and confirmation before destructive actions. The
invite/pairing flow collapses to a single visible **Connect** action.

---

## Localization & TTS

All UI text lives in `res/values/strings.xml` (with `values-ms`, `values-zh`,
`values-ta`). TTS speaks each guide in its chosen language — English, Malay,
Chinese, Tamil — and prompts to install a missing voice, falling back cleanly to
on-screen text.

---

## Privacy (non-negotiable)

StepBuddy **never** captures, stores, autofills, or transmits credentials, OTPs,
live screen content, or payment data. Only caregiver-authored guide content
(text + chosen screenshots) syncs. Caregivers are warned during authoring not to
put account/card/IC numbers in screenshots.

---

## Branding

- applicationId `app.stepbuddy`, invite domain `stepbuddy.app`
- Warm **teal + amber** high-contrast theme
- Generated adaptive **footprints** launcher icon

Change the domain in `app/build.gradle.kts` (`pairingHost`) and
`PairingRepository.INVITE_HOST`.
