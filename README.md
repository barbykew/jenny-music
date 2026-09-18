# Jenny Music

A music player built for Jenny. 🕷️

This is a fork of [SimpMusic](https://github.com/maxrave-dev/SimpMusic) by
[maxrave-dev](https://github.com/maxrave-dev) — a FOSS YouTube Music client for Android and
Desktop, built with Compose Multiplatform. All the heavy lifting (playback, lyrics, the YouTube
Music scraper, Discord Rich Presence, the Spotify Canvas integration) is theirs. Go star it.

## What's different here

- **Pastel theme** — the Material 3 seed colour is a soft rose, and the colour picker in
  Settings offers ten pastel presets (cotton candy, lavender, periwinkle, sky, mint, pistachio,
  butter, peach, clay) instead of the original saturated set.
- **Discord Rich Presence** shows *Jenny Music* with a spider, rather than SimpMusic's branding.
- **Import a Spotify playlist from inside the app.** Upstream can only read a JSON file produced
  by SimpMusic's web converter; here you paste a Spotify playlist link and the app resolves every
  track against YouTube Music itself.

Everything else is upstream SimpMusic and tracks it.

## Importing a Spotify playlist

1. Log in to Spotify once, under **Settings → Spotify**. This is the same login the Canvas and
   lyrics features already use — no Spotify developer account or API key is involved.
2. In Spotify, open a playlist → **Share** → **Copy link**.
3. **Settings → Import from Spotify**, paste the link, confirm.

Each track is matched against YouTube Music by **running time** rather than by title, because a
title alone cannot separate a studio recording from a live take, a sped-up edit or a remix — and
all three routinely outrank the original in search. Anything more than six seconds away from the
Spotify duration is rejected, so a track is left out rather than replaced with the wrong one.
Tracks that could not be matched are counted and reported when the import finishes, since a
playlist that arrives short is otherwise a mystery. YouTube Music genuinely does not carry
everything Spotify does, so some shortfall is normal.

Nothing is written to the database until the whole playlist has been resolved, so cancelling a
long import leaves nothing half-built behind.

## Building

Needs JDK 21 and the Android SDK (`compileSdk 37`, which the SDK manager lists as
`platforms;android-37.0`). Put your SDK path in `local.properties` as `sdk.dir=...`, then:

```bash
./gradlew :androidApp:assembleRelease
```

The `core/` directory is a git submodule, so clone with `--recurse-submodules` or run
`git submodule update --init` afterwards.

## Licence

GPL-3.0, inherited from SimpMusic. See [LICENSE](LICENSE).
