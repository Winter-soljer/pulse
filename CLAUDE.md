# Prism

Unofficial YouTube Music client for Android (Kotlin + Jetpack Compose). Talks directly to
YouTube Music and ~10 lyric services from the phone; no backend. Features word-synced lyrics,
offline downloads, EQ/spatial audio, on-device "Replay" stats, and full Android Auto support.

## Stack
- Kotlin, Compose (Material 3), Media3 ExoPlayer + MediaSession, Room (KSP), DataStore,
  OkHttp, kotlinx.serialization, Coil 3, WorkManager, NewPipe Extractor, Haze (blur).
- JDK 17, compileSdk 37, targetSdk 36, minSdk 26. Package/applicationId: `com.prism.music`.
- Single Gradle module `:app`.

## Commands (Windows: use `.\gradlew.bat`)
- Build release APK: `./gradlew :app:assembleRelease` → `app/build/outputs/apk/release/`
- Debug build: `./gradlew :app:assembleDebug`
- Unit tests: `./gradlew :app:testDebugUnitTest` (some hit live lyric/YT Music endpoints — needs network, can flake)
- Test copy beside the real install: `./gradlew :app:assembleDebug -PsideBySide` (app id `com.prism.music.dev`, debug key,
  its own data; the real Prism isn't touched). Remove it with `adb uninstall com.prism.music.dev`.

## Map (`app/src/main/java/com/prism/music/`)
- `MainActivity.kt` — entry point
- `AppContainer.kt` — manual dependency wiring (no Hilt/Dagger)
- `data/innertube/` — YouTube Music API, response parsing, search ranking
- `data/stream/` — stream URL resolving via NewPipe Extractor
- `data/lyrics/` — lyric providers + TTML/LRC/QRC/YRC/KRC parsers
- `data/db/`, `data/local/` — Room database / local storage
- `data/prefs/` — DataStore settings
- `data/model/`, `data/meta/`, `data/canvas/` — models, metadata, animated covers
- `playback/` — playback service, audio effects, Android Auto browse tree, car lyrics
- `download/` — downloads and smart downloads
- `ui/` — Compose: `screens/`, `player/`, `components/`, `theme/`
- `app/src/test/` — JUnit 4 unit tests
- `docs/screenshots/` — README images only

## Conventions / decisions
- Release builds are **unminified** on purpose: NewPipeExtractor + Rhino use reflection. Don't enable R8.
- Release signing: `keystore.properties` + `prism-release.jks` in the repo root (both gitignored; back them up —
  losing the key means users must reinstall again). Without them, or with `-PdebugSign`, release falls back to the debug key.
  The move off the debug key: 1.3.0 is the last debug-signed release (adds Settings → About → Backup and a
  "needs a reinstall" flow in the update banner when the next APK's signer differs); 1.3.1+ are release-key signed.
  Backup format is `data/Backup.kt` (zip + `prism-backup.json`; restore is staged and applied in `PrismApp.onCreate`).
- Lyrics priority: word-synced → line-synced → plain; user can switch source per song and offset timing.
- Privacy: all history/Replay/settings stay on device; no Prism server. Don't add telemetry.
- Update banner (`data/Updates.kt`): Home asks GitHub's `releases/latest` (≤ every 6 h, always on; Settings → About checks on tap)
  and compares its tag to `versionName`. So every release must bump `versionName`/`versionCode`, use a `vX.Y.Z` tag,
  and attach the `.apk` asset; pre-releases are never offered. Tapping the banner downloads the APK in-app and
  hands it to `PackageInstaller` (one-time "Install unknown apps" permission, then Android's confirm); the APK
  must be Prism with a higher versionCode, signed with the same (debug) key, or it's refused. Shipped in 1.2.1.
- Playlist import (`ui/screens/ImportScreen.kt`, Library → Playlists → "Import playlists") opens TuneMyMusic's
  `tunemymusic.com/transfer/{spotify,apple-music}-to-youtube-music` pages in the browser; the user signs in there and it
  writes to their YT Music account. Prism syncs the library when the user returns. Never pass Prism's YT cookies to it.
  (A built-in link/CSV importer was written first and replaced by this, unreleased, at the owner's request.)
- Fresh-install defaults in `AppSettings` / `HomeSections.initial` are the owner's own settings (smart downloads 1 GB).
- Streams (`data/stream/StreamResolver.kt`): NewPipe resolves anonymously first; when signed in, songs it can't play
  (age-restricted, Music Premium-only, no usable audio) are re-asked via `InnerTube.signedInPlayer` as the TV client
  (`TVHTML5`, cookies + SAPISIDHASH, no PO token), then WEB_REMIX. Bump `InnerTube.TV_CLIENT_VERSION` if TV playback
  starts being refused. Their URLs are ciphered (every cookie-capable client's are), and NewPipe's regexes no longer find
  the signature function, so `PlayerJsSolver` fetches the player JS itself (`iframe_api` → `player_ias.vflset/en_US/base.js`,
  whose signature timestamp the request sends) and solves `s` and `n` with yt-dlp's EJS solver (`assets/ejs/`, v0.8.0,
  from github.com/yt-dlp/ejs releases) in an `androidx.javascriptengine` sandbox, not a WebView page (EJS sets
  `globalThis.location`, which would navigate a page). If YouTube breaks it, first drop in a newer EJS release.
  The next queue item's stream is resolved while the current one plays (`PlaybackService.prefetchNext`).
- Lossless sync (`download/LosslessSync.kt`, 1.4.1): with lossless playback + the lossless add-on on, FLAC/WAV/AIFF
  files on the phone are looked up on YT Music and shown in Downloads (`DownloadInfo.lossless`), playing from the file.
  Runs on launch, ~15 s after MediaStore changes, and every 3 h (`LosslessSyncWorker`). Removing one only hides it
  (`lossless_hidden` prefs); Prism never deletes or moves the user's files.
- Frosted pages (`ui/components/Frosted.kt`, after Apple Music's iOS 27 artist pages): artist, album and playlist pages draw
  `FrostedBackdrop` (the hero picture software-blurred via `ui/theme/Blur.kt` so it works below Android 12, lined up under the
  hero, which `dissolveBottom()` fades into it; its foot row then runs down the page and settles into `ArtPalette.settle`).
  `ArtworkAccent` swaps the page's `primary` for the artwork's colour. Off switch: Appearance → "Frosted artist & album pages"
  (`frostedPages`). Content on them uses `FrostedPanel` / `frostFill()`; `FrostedBar` always blurs (ignores the Glass setting).
  The base screens (Home, Search, Library, Replay, Settings and their sub-pages) share the look: `ScreenBackdrop` draws
  `AmbientFrost` (frost from the playing / last-played cover) as the Default background and provides `LocalFrosted` +
  `LocalPageBackdrop`; shared pieces then go frosted on their own (`Modifier.pane`, `featurePane`/`featureInk`, `quietFill`,
  `RoundAction`, `PrismChip`, `Segmented`, settings `Group`), and `SubPage`/`TopScrollEdge` melt content under the bar.
  Base screens keep the user's accent (no `ArtworkAccent`).
- Artist signatures: `ui/theme/ArtistType.kt` (styles incl. Autograph = `FontFamily.Cursive`, and Outline/Neon/Gradient
  effects drawn in `ui/components/Signature.kt`, written on left to right). Automatic picks stay genre-based for 2M+ audiences;
  a per-artist pick (long-press the name, or ⋯ → Signature style) lives in `data/ArtistPrefs.kt` (`artist_prefs` prefs, which
  also hold starred artists). Starring = YouTube `subscription/subscribe` on `ArtistPage.channelId` when signed in (that's
  the artist's real channel, not always the topic-channel browse id). Real artist logos would need a third-party API key
  (TheAudioDB's free tier is too limited), so they're not used.
- Sharing: `ui/components/ShareSheet.kt` (story card recorded at 1080 px wide via a GraphicsLayer, copy/send link, track list
  for Prism-only lists; an owned PRIVATE playlist can be made UNLISTED from the sheet). Links in `data/ShareLinks.kt`, which
  also parses links shared *to* Prism (MainActivity's ACTION_SEND filter, "Play in Prism"; youtu.be/youtube.com → music.youtube.com).
- Playlist editing (`YouTubeMusic`): `editPlaylist` (name/description/privacy actions on `browse/edit_playlist`),
  `removeFromPlaylist` (needs `playlistItemData.playlistSetVideoId`, collected per page into `CollectionPage.setVideoIds` /
  `LiveCollection.setVideoIds`), `setLibrarySaved` (`like/like` on a playlist id; albums use their `OLAK5uy_…` id), and
  `CollectionPage.owned/privacy/savedToLibrary` from the editable header and the header's bookmark toggle.
- Listen Together (`playback/together/`): local network only, no relay. Host = `ServerSocket` on port 47312 (else any port),
  advertised by NSD as `_prismlisten._tcp`; guests need its 4-digit code. One JSON `Msg` per line (`Protocol.kt`, bump
  `Msg.VERSION` on incompatible changes). Host broadcasts `State` (current + next 24 songs, playhead at host's
  `elapsedRealtime`) on changes, seeks and every 3 s; guests sync clocks NTP-style (`ClockSync`) and follow via
  `PlayerConnection.follow` / `replaceUpcoming`, seeking when >650 ms off; a local pause/skip marks them out of step until
  Resync. `QueueState.followingHost` turns off autoplay and dislike-skipping while following. Every song from a peer goes
  through `fromPeer()` (YouTube ids and YouTube image hosts only). Internet (remote) sessions would need a relay server: not built.
- Menus (unreleased, after 1.5.1): song long-press/⋯ (`SongActionsSheet`), album/playlist/artist long-press (`PlayActionsSheet`) and the
  player's ⋯ (`PlayerOptionsSheet`, plus its Lyrics source / timing sheets) share `ui/components/ActionSheet.kt`: the item's artwork as
  frosted glass (`FrostedBackdrop`, `ArtworkAccent`), `ActionHeader` + `SheetIconButton`s, `QuickActions` tiles, `ActionGroup` panes of `ActionItem`s.
- Uncensoring: `Uncensor` (in `Lyrics.kt`) fills starred words from Genius's text, timings kept; applied in `LyricsRepository.fetch`/`saved`
  when `skipCensored` ("Uncensor lyrics") is on. Lyrics.ovh was dropped. Musixmatch was tried as a keyless karaoke source and doesn't work
  (desktop token is a placeholder that matches every song to "NOKIA"; the iOS token needs signed requests): don't retry it.
- Search taste: `TasteRepository.searchTaste()` (a year of plays, likes, starred + library artists) feeds `SearchRank.rank(…, taste)`;
  known artists climb when the query is whole words of their name, and are added if YouTube's artist results miss them.
- Liked songs playlist: `data/LikedPlaylist.kt` mirrors likes into an owned YT playlist (oldest first, adds/removes; full reconcile after
  library sync). Library playlist order: `LibraryRepository.playlistOrder`/`playlistSort` (`library_order` prefs), Rearrange mode uses
  `ui/components/Reorder.kt`. Artist pages list up to 20 top songs (from the "See all" playlist).
- Picture-in-picture: `MainActivity.setPipEligible`/`enterPip` (auto-enter on 12+, `onUserLeaveHint` below), `LocalPip` makes
  `NowPlayingScreen` show only the video (`FullscreenVideo(pip = true)`); PiP controls come from the media session.
- 1.5.3: Home "Station" cards (e.g. "Listen together": `watchEndpoint` with an `RD…` playlist + seed video +
  params) parse as `PlaylistItem(isMix, seedVideoId, params)` and start a radio on tap (`Navigator.playMix`); they used to parse as
  the seed song. Artist "See all" discographies (`Shelf.grid`) show as a 2-column grid sorted by `Releases` (YouTube appends
  live/remix runs out of order). Floating top-bar buttons use `FloatingDisc` (near-solid, haze can't blur in the NavHost).
  Tabs keep their own back stacks (`saveState`/`restoreState`), so Settings reopens on its last page. Backgrounds have light and
  dark palettes plus a "Simple" group (accent-coloured patterns). System bar icons follow Prism's theme, not the phone's.
  Checked on the Pixel test copy signed out; stations themselves need a signed-in account to see.
- Live streams (1.5.4): "Listen together" stations open on a **live stream** (logcat showed a ~220-day
  position and a stalled buffer). NewPipe still lists "progressive" audio for live videos, which is only the newest few
  seconds, so Prism played ~5 s and skipped. Now `StreamResolver` marks live formats (`StreamType.LIVE_STREAM` / player
  `videoDetails.isLive`, `hlsManifestUrl`), `resolve()` throws `LiveStreamException`, and the item becomes `Song.isLive`
  → `prism://live/<id>` → `HlsMediaSource` via `AppContainer.liveDataSourceFactory()` (plain GETs on `http`, no stream
  interceptor, no cache). Detected up front for stations (`playMix`) and the next item (`prefetchNext`), else on the first
  error. Live items skip the "song version" lookup; the player shows ● LIVE. `LiveStreamLiveTest` checks HLS.
  Live video (1.5.5): the HLS variants carry video too, so the Video button on a live item keeps the same
  `prism://live/<id>` URI (no reload) and only sets `EXTRA_VIDEO`; `PlaybackService.applyLiveVideo` disables the video track
  type while a live item is in audio mode and re-enables it otherwise. Checked on the test copy (lofi radio: video, full
  screen, back to audio, then a normal song's music video).
  Live audio quality: YouTube live manifests only offer two audio renditions, HE-AAC ~48 kbps (itag 233, `mp4a.40.5`) and
  AAC-LC 128 kbps (itag 234, `mp4a.40.2`), no Opus. `BestLiveAudioParserFactory` (`playback/LiveAudio.kt`) drops the HE-AAC one
  from the parsed manifest, so live always plays 128 kbps (logcat tag `PrismLive`). 128 kbps is the ceiling for live.
- 1.5.6: "Keep playing videos" (`videoContinues`, Settings → Playback, on by default): once the listener picks Video
  (`PlaybackService.videoWanted`, set by `PlayerConnection.setVideoMode`), the next song is lined up as its video ahead
  (`armNextVideo`, from `prefetchNext`), or switched on arrival (`continueVideo`); picking Song turns it off and un-arms
  the queue. Live streams come from youtube.com (WEB client, anonymous, `InnerTube.webPost`): Search's "Live" chip
  (`SearchFilter.LIVE`, search param `EgJAAQ==`) and an artist page's "Livestreams" row (the channel's Live tab,
  `YouTubeMusic.liveStreams` on `ArtistPage.channelId`), parsed by `LiveStreams` (on-air only). `LiveStreamsLiveTest`
  checks both on Lofi Girl. White accent: greys have no hue (HSL reads 0° = red, and the scheme builder forced 30%
  saturation, so white came out pink), so `isNeutralSeed` → `neutralScheme`: white accents in dark mode, near-black ink
  in light mode (so the Simple pattern backgrounds, inked in `primary`, stay visible). Swatch is now `0xFFFFFFFF`; the
  old `0xFFE0E0E0` still reads as selected. The Default background (frost from the playing cover) is intentional (1.5.1).
- 1.5.7: Player background "Your background" (`PlayerBackground.APPEARANCE`: Home's Appearance background, drawn dark
  under a forced dark scheme since the player is always white-on-dark) and "Show background" (`playerBackdropShow`, lifts
  the veil on it and on Blurred cover). Backgrounds: six more Simple patterns (Diamonds, Zigzag, Halftone, Crosses, Scales,
  Confetti) and a "Designs" group (`ui/theme/Designs.kt`: Constellation, Contours, Mosaic, Bokeh, Silk, Horizon; seeded
  randoms, detail thinning down the page, animations whole-loop so they don't jump). Video downloads: song menu / player
  ⋯ → "Download video" saves the song plus the video's picture (request `vpic:<songId>`, key `video:<videoId>`) and,
  when the video isn't the song, its own audio (`vaud:<songId>`, key `<videoId>`); kept out of the song list
  (`DownloadRepository.videos`), read back by `videoDataSourceFactory` / the audio leg's normal key, so video mode works
  offline (`downloadedVideoId` short-cuts `videoIdFor`). Search fix: `InnerTube.ensureClientVersion` now holds parallel
  requests behind a mutex (Top results' five searches went out without a visitor id), and a title track on top whose
  album YouTube's album search omitted looks the album up from `song.album` (`SearchRank.namesItsAlbum`).
- 1.5.8: Top results always carry a "Live" row (`searchTop` also runs `SearchFilter.LIVE`; `SearchRank.rank` puts it
  second and never lets a stream take the top card unless nothing else matched). Contours and Silk stuttered on the
  Pixel: they rebuilt ~50k path points every frame, and a page draws its background twice (page + `TopScrollEdge`).
  Now their paths are built once per size (`pathCache` in `Designs.kt`) and slid with `translate` (whole waves per
  screen width, so the loop is seamless; the summit rings rotate a third of a turn, matching their 3/6-fold wobble).
  Light mode: Constellation draws a star chart (`starChart`) instead of grey specks, Horizon's horizon is at 30% height
  (sun behind the title, not the list) with a dawn sky in light mode, Silk is stronger, Halftone stays in its corner.
  Downloads' header shows music videos' count and size (and a "Delete music videos" menu entry).
- Backgrounds can be looked at without a phone: `BackdropRenderTest` (Robolectric native graphics, test-only dependency)
  draws every Simple and Designs background in dark, light and light-with-white-accent onto contact sheets in
  `app/build/backdrop-previews/`, with the screens' wash and mock rows. Run it and look at the PNGs after changing any
  background. The drawing lives in plain `DrawScope.drawDesign` / `drawPattern` functions for that reason.
- Not affiliated with Google/YouTube — keep the disclaimer in README.
- `*.apk`, `local.properties`, keystores are gitignored.

## Current status / Next steps
- **v1.5.8 (versionCode 23) is GitHub "Latest"** (2026-10-11, `Prism.apk` release-key signed): Live row in Top results,
  smooth Contours/Silk, light-mode background fixes, video storage in Downloads (see "1.5.8" above). 62 unit tests;
  all pass except `LiveApiTest.downloadThroughput`, which failed once in the full run (YouTube throttled its raw
  download to 30 KB/s) and passed when re-run alone. Backgrounds checked on rendered contact sheets; not on a device.
- v1.5.7 (versionCode 22, commit `a1c331c`, 2026-10-11; GitHub "Latest" until 1.5.8): player
  "Your background" + "Show background", 12 new backgrounds, video downloads, Top results fix (see "1.5.7" above). All 59
  unit tests pass. Not checked on a device: the new designs' look (Horizon is the busiest), video downloads end to end
  (download, airplane mode, Video button), Your background in the player with a photo and with a Design.
- Testing on the phone: as of 1.5.6 the owner said not to test on their phone; they install the GitHub release and test
  it themselves. So: build, unit-test, release, and list what to try. There's no emulator on this PC (`adb devices` shows
  only the owner's phone), so UI changes can't be looked at here; review drawing code carefully instead.
- v1.5.6 (versionCode 21, commit `1c942ac`, 2026-10-11): keep playing videos, Live search, artist Livestreams, pure white
  accent (see "1.5.6" above). Was GitHub "Latest" until 1.5.7. Built and unit-tested (live tests pass on Lofi Girl);
  not checked on the phone (the owner tests it).
  `ArtistAlbumLiveTest.topResultsAreRelevant` failed then; fixed in 1.5.7.
- v1.5.5 (versionCode 20, 2026-10-11): live video (the Video button on live streams) and 128 kbps live audio (see
  "Live streams" above). Checked on the Pixel test copy; PiP for live not tried.
  `ArtistAlbumLiveTest.topResultsAreRelevant` ("after hours" top result) failed from here until 1.5.7 fixed it.
- v1.5.4 (versionCode 19, 2026-10-11): live streams play from HLS, so "Listen together" stations work (see "Live streams"
  above). Live playback checked on the Pixel test copy (a 24/7 lofi
  radio); not yet checked: the station itself on the owner's signed-in Home.
- v1.5.3 (versionCode 18, 2026-10-11): stations start a radio, artist discography grid + sorting, legible floating buttons,
  light/dark backgrounds + Simple designs, tabs keep their pages, system bars follow Prism's theme (see "1.5.3" above).
  Its station fix wasn't enough (the station opens on a live stream); fixed in 1.5.4.
- v1.5.2 (versionCode 17, 2026-10-10): frosted menus, uncensored lyrics, taste-aware search, Liked songs playlist, playlist
  rearranging, 20 top songs, PiP (features in `a9a8fd8`, see the notes above). Was GitHub "Latest" until 1.5.3.
  Built and unit-tested (plus a live uncensor test); not yet checked on the phone: PiP and its media-session controls, the Liked songs
  playlist against a real account, the new menus in light/dark.
- `.claude/settings.local.json` no longer denies reading `build/` or `*.apk` (the owner lifted it for releases).
- v1.5.1 (versionCode 16, 2026-10-10): Home, Search, Library, Replay and Settings rebuilt on the 1.5 frosted look
  (see "Frosted pages" above). Commit `ff71b95`; not yet checked on the phone.
- v1.4.4 (versionCode 14, 2026-10-10): plays reach the account's YT Music history again
  (checked on the phone). The WEB_REMIX `player` call (`YouTubeMusic.playerExtras`) answers "Video unavailable" with no
  `videostatsPlaybackUrl` unless it sends the player JS's `signatureTimestamp` (`StreamResolver.signatureTimestamp()`).
  Logcat tag `PrismHistory` logs each ping's HTTP code. Don't use TV-client tracking URLs for history: YouTube takes
  their pings (204) but never adds them to YT Music history.
- v1.4.3 (versionCode 13, 2026-10-10): tried that TV-client tracking URL; plays still didn't reach history.
- v1.4.2 (versionCode 12, 2026-10-09): age-restricted and Music Premium-only songs play when
  signed in, deciphered by `PlayerJsSolver` (checked on the phone: "Neighbors" by J. Cole, BMTH "Fuck", seeking).
- v1.4.1 (versionCode 11, 2026-10-09): lossless sync (lossless files on the phone show up in Downloads, rescanned on
  media changes and every 3 h) and the next song's stream is resolved ahead. Its signed-in playback code didn't work yet
  (NewPipe's signature regexes are stale), so its notes left that out. 1.4.0 (versionCode 10) was skipped: bumped but never released.
- v1.3.3 (versionCode 9, 2026-10-08) was the previous release: plain outline icons on the Settings hub (no coloured badges),
  sign-in leaves the WebView the moment YouTube's session cookies appear (account info + library sync run after Prism opens),
  and update checks are always on: the `checkUpdates` setting is gone; Settings → About → "Check for updates" asks GitHub now
  (`UpdateChecker.checkNow()`, also un-dismisses the banner).
  v1.3.2 (versionCode 8, 2026-10-07): Library → Import playlists (TuneMyMusic hand-off), and long-press
  any album/playlist/artist card or Home shortcut tile to play or shuffle it (`ui/components/PlayActions.kt`).
  v1.3.1 (versionCode 7): first release signed with the release key (cert SHA-256 `cbc462bc…`).
  v1.3.0 (versionCode 6): backup & restore, last debug-signed build. v1.2.2 (versionCode 5): drag to reorder Customize Home sections, "Greeting & shortcuts"
  renamed "Shortcut tiles" (greyed-out tile settings when that section is off), long-press a Library playlist to delete it.
  v1.2.1 (versionCode 4, 2026-10-07): in-app update install. v1.2.0 (`f3656fa`) added music videos as songs,
  cleaner playlists/Customize Home and the update banner. v1.1.0 (2026-10-06, `70b6251`) was the first official release;
  v1.0.0 (2026-10-04) is marked pre-release.
- After every build that's released (or handed over), update this file in the same commit: the version's notes, which
  release is "Latest", what was and wasn't checked (the owner asked for this).
- Release flow: build `assembleRelease`, upload the APK as `Prism.apk` (README links to
  `releases/latest/download/Prism.apk`), notes include the Android Auto "parked/passengers only" warning.
  Check the signer before uploading (`apksigner verify --print-certs` → SHA-256 `cbc462bc…`). Commit with
  `git commit -F <file>` (a multi-line `-F -` here-string in PowerShell failed and git treated the message as paths), and
  only tag after the commit succeeded — the first v1.5.0 tag landed on the 1.4.4 commit and had to be deleted and redone.
  `gh release create vX.Y.Z Prism.apk --title "Prism X.Y.Z" --notes-file notes.md --latest` worked from this machine.
- PowerShell 5.1 encoding trap: `Get-Content -Raw` reads BOM-less UTF-8 as ANSI and `Set-Content -Encoding utf8` adds a BOM,
  so a read-replace-write mangles `·`, `—`, `…`, `•` (e.g. `" • "` → `" â€¢ "`). It hit `CollectionScreen.kt` and
  `build.gradle.kts` during 1.5.0 (both repaired). Use the Edit tool, or `[IO.File]::ReadAllText/WriteAllText` with
  `UTF8Encoding($false)`; afterwards grep for `â€|Â·` to be sure.
- Phone checks: the Pixel 10 Pro XL is the owner's personal phone (wireless adb). Ask before touching it; use the
  side-by-side test copy (`-PsideBySide`, `com.prism.music.dev`) rather than replacing the real install. Find tap targets with
  `adb shell uiautomator dump` (match `text` or `content-desc`), decline the test copy's notification prompt, never act in
  the system share chooser (it shows personal contacts), and afterwards `rm` temp files from /sdcard, `am force-stop` the
  test copy and press Home. A PC on the same Wi-Fi can join a Listen Together session as a test guest with a raw TCP
  client sending `{"t":"hello","name":"Test PC","code":"NNNN","version":1}` (don't send `add`: it starts playback).
  The `com.prism.music.dev` copy from the 1.5.0 checks may still be on the phone (`adb uninstall com.prism.music.dev`).
- README screenshots (`docs/screenshots/`) were retaken for 1.1 on a Pixel 10 Pro XL with personal info
  and the mini player blurred (no way to hide the mini player in release builds). Keep blurring on retakes.
  `video.jpg` (music video playing, Kid Cudi) was added 2026-10-06 with the README "Music videos" feature notes.
  The player title is a looping marquee: take a burst of screencaps and pick the frame where it rests at the start.
  Use `adb shell screencap` + `adb pull` (PowerShell `exec-out >` redirection corrupts PNGs).
- README has a "Credits and inspiration" section (Apple Music, BitChord, Spotify Wrapped, lyric/canvas
  sources, libraries) — update it when borrowing new ideas or sources.
- Donations: Ko-fi at https://ko-fi.com/yigder — linked from a README header badge, a "Support Prism"
  section (before "Building from source"), and `.github/FUNDING.yml` (`ko_fi: yigder`, GitHub Sponsor button).
  Keep it optional/no-pressure; no in-app donation prompts unless the user asks.
- On the user's Wi-Fi, `gh` API calls have failed (`invalid character '<'`) while git push works; if so, ask them to
  switch to hotspot. (`gh release create` worked for 1.5.6 and 1.5.7.)
- An untracked `Prism/` subfolder duplicates the project (with build output and logs) — decide whether to delete it.
- v1.5.0 (versionCode 15, commit `75c5c7f`, 2026-10-10): the QOL update — frosted artist/album/playlist pages, artist
  signatures and favourites, share sheet + "Play in Prism", Listen Together, playlist edit/remove/save-to-library/new playlist,
  add-to-playlist sheet, queue save/clear, sleep "end of song". Built and unit-tested; checked on the Pixel with the
  side-by-side test copy (signed out): artist page dark + light, signatures, album/playlist pages, share sheet + image
  export, Listen Together hosting (a PC guest joined over Wi-Fi; wrong code refused; NSD advert seen). Not yet tried:
  two phones following each other, signed-in actions (save to library, edit/remove, favourite → subscribe).
  Haze blur doesn't draw for elements inside the NavHost's own hazeSource, hence `ScrollEdge` instead of a blurred bar.
- Next steps: hear back from the owner's own testing of 1.5.6 to 1.5.8 (keep playing videos, Live search, the Live row
  in Top results and artist Livestreams, the white accent in light and dark, the backgrounds and whether Contours/Silk
  are smooth now, Your background in the player, video downloads offline and their size in Downloads). Still untried from 1.5.0: two phones in a Listen Together session, and signed-in save to library, playlist
  edit/remove/delete and favourite → subscribe. Possible later: remote (internet) Listen Together, which needs a
  relay server and so an owner decision given the no-server rule; real artist logos, which need a third-party API key.
