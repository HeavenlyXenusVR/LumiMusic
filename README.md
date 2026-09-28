# LumiMusic

The Android edition of [Lumisound](https://github.com/HeavenlyXenusVR/Lumisound) — same
account, same bridge, same cloud library.

LumiMusic is not a separate service with an import button bolted on. It talks to the
**same `ios-bridge` backend** Lumisound does, with the same `/auth/*` session tokens and the
same per-user cloud storage, so:

- **Cross sign-in.** Sign in with the username and password you already use on iPhone. No
  linking step, no LumiMusic account, no migration. An account created here works in
  Lumisound too.
- **Your cloud tracks play here.** Everything in your personal server library
  (`/user/music`) is browsable and streamable, including Lumisound-locked `.lms` tracks,
  which no other Android player can open at all — LumiMusic unmasks them as they stream
  (see `LumisoundLockDataSource`).
- **Your cloud data comes with you.** Favorites, playlists, play history and the shared
  settings row are pulled down by **Settings → Import cloud data**, and every play logged
  here counts toward the same history, scrobbles, stats and achievements as a play on iOS.

## Status

Milestone 1 (account + cloud import + cloud playback) is what exists today. See
[docs/ROADMAP.md](docs/ROADMAP.md) for what is built, what is deliberately out of scope,
and what comes next.

## Building

No Android Studio required; CI builds every push.

```bash
./gradlew assembleDebug
```

Two optional, gitignored files:

| File | What it's for |
|---|---|
| `secrets.properties` | `lumiBridgeApiKey=…` — the shared bridge key for the bridge's legacy yt-dlp-backed routes. Unset just means no key is sent. |
| `keystore.properties` | `storeFile` / `storePassword` / `keyAlias` / `keyPassword` for a signed release build. |

Neither is needed to build, run, or sign in.

## Architecture at a glance

```
bridge/        Retrofit APIs, auth interceptor, token store, URL builders
cloud/         CloudImportService — pulls account data into the local mirror
data/db/       Room mirror of server state (cloud tracks, favorites, playlists, history)
playback/      Media3 session service, lock-aware DataSource, play-history logger
ui/            Compose screens (sign-in, cloud library, import, now playing, settings)
```

Dependencies are wired by hand in `AppContainer` — one app-scoped object of lazy
singletons, no annotation-processed DI.

## License

See [LICENSE](LICENSE).
