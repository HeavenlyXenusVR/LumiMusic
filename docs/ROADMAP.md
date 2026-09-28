# LumiMusic port roadmap

Lumisound (iOS) is a mature app: ~537 Swift files and a bridge exposing 281 endpoints.
This document tracks what the Android port has, in the order it was built, and is the
honest list — a feature is only "done" here if it is wired to a live consumer, not merely
modeled.

## Milestone 1 — account, cloud import, cloud playback (built)

| Piece | State | Notes |
|---|---|---|
| Sign in / register / 2FA continuation | Done | `/auth/login`, `/auth/register`, `/auth/2fa/login`. Same account as iOS. |
| Session persistence | Done | Token in EncryptedSharedPreferences (the Keychain analogue), validated against `/auth/me` on launch; a 401 means revoked and signs out, a network failure does not. |
| Cloud library listing | Done | `GET /user/music`, mirrored into Room so the library survives offline and cold starts. |
| Cloud search | Done | Local (indexed) search over the mirror; `GET /user/music/search` is modeled for server-side search. |
| Cloud playback | Done | Media3 `MediaSessionService`, authenticated streams over the shared OkHttp client. |
| Locked `.lms` playback | Done | Unmasked **in flight** by `LumisoundLockDataSource` — no full-file download and no plaintext copy on disk, unlike iOS/tvOS which unlock to a temp file first. |
| Loudness normalization | Done | `X-Loudness-Gain-Db` from the stream response applied as player gain. |
| Cloud data import | Done | Favorites, playlists (+ tracks), play history, shared settings. Per-stage progress; a failed stage never discards the stages that succeeded. |
| Play-history logging | Done | `POST /user/history` ~5s into a track (the same accidental-skip filter iOS uses), which is also what drives scrobbling, the Discord webhook and achievements server-side. |
| Accent from account | Done | `theme_color` is the one genuinely cross-platform settings field; the rest of that row is iOS-shaped audio config with no Android equivalent. |

### Deliberately not done in milestone 1

- **`POST /user/sync`.** It unconditionally DELETEs the caller's favorites and playlists
  before checking whether the request supplied any, so a settings-only push through it
  silently wipes both. The individual endpoints have no such asymmetry and are used
  instead. Any future adoption of `/user/sync` must GET first and echo `favorites` /
  `playlists` back verbatim.
- **Writing playlists / favorites from Android.** The write endpoints are wired in the
  repository but no editing UI exists yet; read-only beats half-built for shared data.
- **Local on-device library.** LumiMusic is cloud-first in this milestone. A MediaStore
  scanner is milestone 2.

## Verification status — read this before trusting the table above

Everything in milestone 1 **compiles, passes unit tests and passes lint in CI** on every
push, and the `.lms` lock transform is verified byte-for-byte against the bridge's own
`locked_media._unmask_into` across random seeks and key-phase boundaries (six unit tests
cover it going forward). The bridge's auth error shape (`{"detail": "..."}`) and its 401 on a
bad token — the signal the app uses to tell a revoked session from a network blip — were
confirmed against the live server.

**Not yet verified:** the app has never been run on a device or emulator. No sign-in, import
or playback has been exercised against a real account. Everything about runtime behaviour is
"should work", not "seen working" — the first device run is the next verification step.

## Milestone 2 — the local library and the player proper (next)

- MediaStore/SAF library scan, folders, artists, albums, genres
- Queue management, shuffle/repeat, gapless, crossfade, EQ
- Favorites/playlist editing, pushed to the same endpoints iOS uses
- Downloads of cloud tracks for offline play

## Milestone 3+ — the breadth surface

Roughly in bridge-endpoint order, each independently portable: discover mix / on this day /
trending / similar listeners, subscriptions + new-release feed, podcasts (+ chapters, OPML,
episode progress), social (friends, presence, profiles, leaderboards, activity), scrobbling
account linking, achievements, stats / year-in-review, collaborative playlists, lyrics
(incl. Whisper transcription), listening rooms, AcoustID identification, BPM analysis,
smart playlists, Aria's daily pick and the rest of the intelligence endpoints.

Known non-portable: Discord **Rich Presence** (needs the desktop IPC daemon), SharePlay
"Listen Together", spatial audio (genuine HRTF rendering with head tracking).
