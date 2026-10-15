<div align="center">

# Prism

**A YouTube Music client for Android, with word-by-word lyrics, Replay, offline downloads and full Android Auto support.**

[![Latest release](https://img.shields.io/github/v/release/Yigder/Prism?label=download&color=7c4dff)](../../releases/latest)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3ddc84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7f52ff?logo=kotlin&logoColor=white)
![No tracking](https://img.shields.io/badge/telemetry-none-555)
[![License: GPL-3.0-or-later](https://img.shields.io/badge/license-GPL--3.0--or--later-blue)](LICENSE)
[![Support on Ko-fi](https://img.shields.io/badge/Ko--fi-support%20Prism-ff5e5b?logo=ko-fi&logoColor=white)](https://ko-fi.com/yigder)

**[⬇ Download Prism.apk](../../releases/latest/download/Prism.apk)** · [Release notes](../../releases/latest) · [▶ Watch the showcase (1:55)](promo/prism-showcase.mp4)

<img src="docs/screenshots/home-dark.jpg" width="22%" alt="Home">
<img src="docs/screenshots/player.jpg" width="22%" alt="Now playing">
<img src="docs/screenshots/lyrics.jpg" width="22%" alt="Word-by-word lyrics">
<img src="docs/screenshots/artist-dark.jpg" width="22%" alt="Artist page">

</div>

> [!NOTE]
> Prism is an independent, unofficial app. It is not affiliated with, endorsed by, or connected to Google, YouTube or YouTube Music.

## Install

1. Download **`Prism.apk`** from the [latest release](../../releases/latest) on your phone.
2. Open it. Android will ask you to allow installs from your browser or file manager.
3. Optional: sign in with your YouTube Music account to sync likes, playlists, albums and artists.

Updating works the same way: install the new APK over the old one, and your downloads, Replay history and settings stay.

## Highlights

| | |
|---|---|
| 🎤 **Word-by-word lyrics** | Ten lyric sources, karaoke timing first, then line-synced, then plain. Switch source per song and nudge the timing. |
| 🎨 **Make it yours** | Font, text size, corners, spacing, nav bar, mini player, player background and controls, lyrics alignment, Replay colours and eleven screen backgrounds. |
| 📊 **Replay** | A private, on-device recap of your listening, with a full-screen story. |
| 🎬 **Music videos** | Switch any song to its music video in one tap, with lyrics in sync and full-screen landscape. |
| 🎧 **Listen together** | Phones on the same Wi-Fi play in step, or friends add songs to your queue. No account, no server. |
| 🔗 **Share anything** | Story cards and links for songs, albums, playlists and artists, plus "Play in Prism" for YouTube links. |
| 📥 **Offline** | Downloads, Smart Downloads and your own music files, with lyrics saved alongside every song. |
| 🎚️ **Sound** | 10-band EQ, loudness normalization, Prism Spatial, skip silence and lossless playback. |
| 🎛️ **Crossfade** | Songs fade into each other as on Spotify, with the length worked out for each pair, or beat-matched like a DJ set. |
| 🚗 **Android Auto** | Shuffle play, extra player buttons and lyrics on the car screen. |

## Features

### Listening
- **Lyrics, word by word.** Better Lyrics (Apple Music's timed lyrics), QQ Music, NetEase, KuGou, LyricsPlus, Bini, LRCLIB, Unison, Genius and YouTube Music. Starred-out swear words ("f**k") are filled back in from Genius's full text, keeping the word-by-word timing. Lyrics in another language can show a translation under each line.
- **Crossfade.** When a song runs into the next, the ending song fades out while the next one fades in, as on Spotify. With **Automatic length**, Prism listens to how each song ends and the next begins (measured on the phone, on Wi-Fi): it crossfades over a song's fade-out or the next song's quiet intro, and only briefly between two hard edges; or pick 1 to 12 seconds yourself. **Match tempo and beats** makes it DJ-style: the next song starts on the beat and plays at the same tempo while they overlap, then eases back to its own. An album's own tracks still play straight through.
- **Player.** Full-bleed artwork and animated covers (saved with downloads, for offline), in three cover sizes up to full width, with optional shuffle and repeat buttons beside play. Shows the codec and bitrate (or Lossless / Downloaded) under the progress bar, with stats in an in-player panel.
- **Lyrics in any direction.** Arabic, Hebrew and other right-to-left lyrics sit on the right and light up from the right.
- **Podcasts.** Popular shows, your followed shows when signed in, episodes that pick up where you left off, and 0.75× to 2× speed.
- **Music videos.** Tap the video toggle under the player to switch any song to its official music video. Playback carries on from the same spot, and the lyrics stay in sync. Fit or fill the frame, go full screen, or turn the phone sideways for edge-to-edge landscape. Video quality goes from 480p up to 1440p in Playback & sound.
- **Sound.** 10-band equalizer with presets, bass boost, loudness normalization (each song measured on the phone, to -14 LUFS; works offline), Prism Spatial for any headphones, skip silence, and a lossless mode that plays matching FLAC files on the phone.
- **Immersive mode.** The cover fills the screen (its animated version when it has one) and the lyrics play over it like a lyric video. Swipe for the next or previous song; pick how the cover fits and where the lyrics sit.
- **Autoplay and radio.** The queue keeps going with similar music after an album or playlist ends. Shuffle makes a fresh order every time, and Previous goes back to the song you just heard. Swipe a song in an album or playlist right to play it next, or left to add it to the queue. Save the queue as a playlist, or clear what's up next.
- **Sleep timer** for a set time or until the current song ends.

### Finding music
- **Search as you type**, with a ranked "Top results" view across songs, albums, artists, playlists and videos.
- **Artist pages** in the style of Apple Music's 2026 redesign: the photo frosts over into a backdrop made of its colours, the name is set in a signature typeface that suits the artist (long-press it to pick your own, from Autograph to Neon), and Info, Play, Shuffle and Favourite sit right under it. The latest release gets a spotlight card, then top songs, albums, singles, videos, live, appearances and similar artists.
- **Albums** star their most-played songs, show the release type, year and explicit badge, and end with other versions and more by the artist. **Playlists** sort by title, artist, album, duration or genre, and filter by genre. Both sit on the same frosted backdrop (switchable in Appearance), with big Play and Shuffle buttons.

### Your library
- **Home your way.** Custom greeting, shortcut tiles and pinned playlists.
- **Downloads** work like a playlist, with counts, size, saved lyrics, sorting and bulk delete. Smart Downloads is one space for music on the phone: the songs you stream, plus the ones you're likely to play next, downloaded ahead (on Wi-Fi, or mobile data if you allow it). Library → Local files plays the music files already on your phone, by song, artist or album.
- **Replay.** Minutes, top songs, artists and genres. Play or shuffle your top 250.
- **Custom playlist pictures** and **backgrounds** for Home, Search, Library, Replay and Settings.
- Signed in, likes, playlists, albums and artists sync both ways. **Add albums and playlists to your library**, **star your favourite artists** (they lead Library → Artists and are subscribed on YouTube Music), **make new playlists**, and add songs to them from a searchable sheet.
- **Your playlists, your way:** rename them, rewrite the description, choose private, unlisted or public, take songs out, or delete them.
- **Import playlists from Spotify, Apple Music and more** with [TuneMyMusic](https://www.tunemymusic.com) (Library → Import playlists). It copies them into your YouTube Music account, and Prism syncs them in when you come back.

### Together and sharing
- **Listen together.** Start a session and friends on the same Wi-Fi (or your hotspot) join with a four-digit code. They can play the same music on their own phones, kept in step with yours, or just add songs to your queue, party style. You decide whether guests can also play, pause and skip. Phones talk to each other directly: there's no Prism server, and nothing is shared once the session ends.
- **Share** any song, album, playlist or artist as a link or as a story card made from its artwork. A private playlist's link only works for you, so the share sheet offers to make it unlisted. Liked songs and Downloads share as a card and a track list.
- **Play in Prism:** share a YouTube or YouTube Music link from any app to Prism to open it.

### Android Auto
- Browse Home, Liked songs, your library and recent plays, and search by voice.
- Every song list starts with **Shuffle play**.
- Extra buttons: like, shuffle, repeat, lyrics on/off, start radio and "not for me".
- **Lyrics on the car screen.** The sung line replaces the title, and the cover becomes a lyric card that lights up word by word.

> [!WARNING]
> **Android Auto lyrics are for passengers and for use while parked only.** Do not read lyrics while driving. Keep your eyes on the road and follow local laws. You can turn car lyrics off at any time in Settings → Lyrics → Android Auto, or with the lyrics button on the car's player.

## Screenshots

### New in 1.6

| | | | |
|:-:|:-:|:-:|:-:|
| <img src="docs/screenshots/immersive.jpg" width="190" alt="Immersive mode: the cover fills the screen with the lyrics over it"><br>Immersive mode | <img src="docs/screenshots/crossfade-dark.jpg" width="190" alt="Crossfade settings"><br>Crossfade, timed per song | <img src="docs/screenshots/local-dark.jpg" width="190" alt="Local files page"><br>Local files | <img src="docs/screenshots/lyrics-rtl.jpg" width="190" alt="Arabic lyrics, right-aligned, with a translation"><br>Right-to-left lyrics |
| <img src="docs/screenshots/player-large.jpg" width="190" alt="Player with the Large cover size"><br>Large cover | <img src="docs/screenshots/player-full.jpg" width="190" alt="Player with the Full width cover size"><br>Full width cover (current lyric and output device hidden) | <img src="docs/screenshots/storage-dark.jpg" width="190" alt="Smart Downloads settings"><br>Smart Downloads, one space | <img src="docs/screenshots/podcasts-dark.jpg" width="190" alt="Podcasts"><br>Podcasts |

### The player

| | | | |
|:-:|:-:|:-:|:-:|
| <img src="docs/screenshots/player.jpg" width="190" alt="Now playing"><br>Now playing | <img src="docs/screenshots/lyrics.jpg" width="190" alt="Lyrics"><br>Word-by-word lyrics | <img src="docs/screenshots/video.jpg" width="190" alt="Music video playing in the player"><br>Music videos, one tap from the song | <img src="docs/screenshots/queue.jpg" width="190" alt="Queue"><br>Queue, shuffle and autoplay |
| <img src="docs/screenshots/player-blur.jpg" width="190" alt="Player with the blurred cover background and ring controls"><br>Blurred cover, ring controls | <img src="docs/screenshots/player-theme.jpg" width="190" alt="Player with the theme background and classic controls"><br>Theme background, classic controls | <img src="docs/screenshots/playerset-dark.jpg" width="190" alt="Player settings, dark"><br>Player settings | <img src="docs/screenshots/playerset-light.jpg" width="190" alt="Player settings, light"><br>Player settings, light |

### Light and dark

Every screen follows the phone's theme, or pick Light or Dark in Appearance.

| | | | |
|:-:|:-:|:-:|:-:|
| <img src="docs/screenshots/home-dark.jpg" width="190" alt="Home, dark"> | <img src="docs/screenshots/home-light.jpg" width="190" alt="Home, light"> | <img src="docs/screenshots/search-dark.jpg" width="190" alt="Search, dark"> | <img src="docs/screenshots/search-light.jpg" width="190" alt="Search, light"> |
| Home with Replay and shortcuts || Search with top results ||
| <img src="docs/screenshots/artist-dark.jpg" width="190" alt="Artist page, dark"> | <img src="docs/screenshots/artist-light.jpg" width="190" alt="Artist page, light"> | <img src="docs/screenshots/album-dark.jpg" width="190" alt="Album, dark"> | <img src="docs/screenshots/album-light.jpg" width="190" alt="Album, light"> |
| Artist pages with signatures || Albums ||
| <img src="docs/screenshots/playlist-dark.jpg" width="190" alt="Playlist, dark"> | <img src="docs/screenshots/playlist-light.jpg" width="190" alt="Playlist, light"> | <img src="docs/screenshots/library-dark.jpg" width="190" alt="Library, dark"> | <img src="docs/screenshots/library-light.jpg" width="190" alt="Library, light"> |
| Playlists with genre filters || Library ||
| <img src="docs/screenshots/downloads-dark.jpg" width="190" alt="Downloads, dark"> | <img src="docs/screenshots/downloads-light.jpg" width="190" alt="Downloads, light"> | <img src="docs/screenshots/together-dark.jpg" width="190" alt="Listen together, dark"> | <img src="docs/screenshots/together-light.jpg" width="190" alt="Listen together, light"> |
| Downloads, with lyrics saved || Listen together ||
| <img src="docs/screenshots/replay-dark.jpg" width="190" alt="Replay, dark"> | <img src="docs/screenshots/replay-light.jpg" width="190" alt="Replay, light"> | <img src="docs/screenshots/replay2-dark.jpg" width="190" alt="Replay top genres and songs, dark"> | <img src="docs/screenshots/replay2-light.jpg" width="190" alt="Replay top genres and songs, light"> |
| Replay, your on-device recap || Top artists, genres and songs ||

### Settings

| | | | |
|:-:|:-:|:-:|:-:|
| <img src="docs/screenshots/settings-dark.jpg" width="190" alt="Settings, dark"> | <img src="docs/screenshots/settings-light.jpg" width="190" alt="Settings, light"> | <img src="docs/screenshots/appearance-dark.jpg" width="190" alt="Appearance, dark"> | <img src="docs/screenshots/appearance-light.jpg" width="190" alt="Appearance, light"> |
| Settings || Theme, accent colour and fonts ||
| <img src="docs/screenshots/backgrounds-dark.jpg" width="190" alt="Backgrounds, dark"> | <img src="docs/screenshots/backgrounds-light.jpg" width="190" alt="Backgrounds, light"> | <img src="docs/screenshots/sound-dark.jpg" width="190" alt="Playback and sound, dark"> | <img src="docs/screenshots/sound-light.jpg" width="190" alt="Playback and sound, light"> |
| Screen backgrounds and designs || Lossless, normalization, video quality ||
| <img src="docs/screenshots/eq-dark.jpg" width="190" alt="Equalizer, dark"> | <img src="docs/screenshots/eq-light.jpg" width="190" alt="Equalizer, light"> | <img src="docs/screenshots/carlyrics-dark.jpg" width="190" alt="Android Auto lyrics settings, dark"> | <img src="docs/screenshots/carlyrics-light.jpg" width="190" alt="Android Auto lyrics settings, light"> |
| 10-band equalizer || Android Auto lyrics, with preview ||

| | |
|:-:|:-:|
| <img src="docs/screenshots/menu.jpg" width="190" alt="Song menu"><br>Song menu | <img src="docs/screenshots/welcome.jpg" width="190" alt="Welcome screen"><br>Welcome, with or without an account |

### Android Auto

<img src="docs/screenshots/android-auto-lyrics.jpg" width="600" alt="Prism on Android Auto, the sung line shown in place of the song title">

The sung line shows in place of the song title on the car's player (parked or passengers only).

## Credits and inspiration

Prism doesn't ship artwork, fonts or code copied from other apps, but several ideas and data sources come from elsewhere:

- **Apple Music** inspired the artist page layout (including its 2026 frosted redesign, with the name and Info, Play and Favourite centred under the photo), starred most-played album tracks, headline artist typefaces and the animated cover style.
- **Spotify Jam** inspired Listen Together (built here to work without any server), and Spotify and Apple Music's share cards inspired the story cards.
- **Signatures** use fonts already on the phone, such as Android's own Dancing Script for the Autograph style.
- **BitChord** (itself modelled on Apple Music) inspired the Now Playing screen.
- **Spotify Wrapped** inspired the Replay story, including its "what's playing" card.
- **Spotify's Crossfade and Mix** inspired Crossfade and its beat-matched blends between songs.
- **Lyrics:** [Better Lyrics](https://betterlyrics.org) (Apple Music TTML and QQ Music "Portato"), NetEase, KuGou, LyricsPlus, Bini, [LRCLIB](https://lrclib.net), Unison, Genius and YouTube Music. Lyrics belong to their rights holders.
- **Animated covers** come from Apple Music, Tidal and the ViVi Music canvas list.
- **Playlist import** hands off to [TuneMyMusic](https://www.tunemymusic.com), the transfer service YouTube Music's own app uses. It's a separate service; Prism shares nothing with it.
- **Signed-in streams:** [yt-dlp's EJS solver](https://github.com/yt-dlp/ejs) (Unlicense, bundling [meriyah](https://github.com/meriyah/meriyah) and [astring](https://github.com/davidbonnet/astring)) deciphers them, running on WebView's JavaScript engine through androidx.javascriptengine.
- **Libraries:** [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor), [Haze](https://github.com/chrisbanes/haze), [Coil](https://coil-kt.github.io/coil/), Media3 ExoPlayer and the rest of the stack below.
- Music and metadata are streamed from YouTube Music. All trademarks belong to their owners.

## Privacy

Prism talks to YouTube Music and the lyric services directly from your phone. There is no Prism server and no telemetry. Listening history, Replay stats, downloads and settings stay on the device; if you sign in, your session is stored only in the app's private storage. Listen Together only ever talks to the other phones in your session, on your local network, and only while it's open. Lyrics translation (off unless you turn it on) sends the lyrics' text to Google Translate.

## Support Prism

Prism is free, has no ads and doesn't track you. It's built and maintained in my spare time. If you enjoy it and want to help keep it going, you can buy me a coffee on **[Ko-fi](https://ko-fi.com/yigder)**. Every tip helps, and is never expected.

<a href="https://ko-fi.com/yigder"><img src="https://img.shields.io/badge/Buy%20me%20a%20coffee-Ko--fi-ff5e5b?style=for-the-badge&logo=ko-fi&logoColor=white" alt="Support Prism on Ko-fi"></a>

## Building from source

Requirements: JDK 17+ and the Android SDK (compile SDK 37).

```bash
./gradlew :app:assembleRelease
```

The APK lands in `app/build/outputs/apk/release/`. Without a `keystore.properties` (and its keystore) in the project root, release builds are signed with the debug key so they install directly; official releases use Prism's own key, so a self-built APK installs beside them only after uninstalling the official one. Release builds are deliberately unminified (NewPipe Extractor and Rhino rely on reflection).

Unit tests (some call live lyric and YouTube Music endpoints, so they need a network connection):

```bash
./gradlew :app:testDebugUnitTest
```

### Project layout

| Area | Where (`app/src/main/java/com/prism/music/`) |
|---|---|
| YouTube Music API, parsing and search ranking | `data/innertube/` |
| Stream resolving (NewPipe Extractor) | `data/stream/` |
| Lyric providers and TTML / LRC / QRC / YRC / KRC parsers | `data/lyrics/` |
| Playback service, audio effects, Android Auto and car lyrics | `playback/` |
| Listen Together (local-network sessions) | `playback/together/` |
| Downloads and smart downloads | `download/` |
| Compose UI and design kit | `ui/` |

Stack: Kotlin, Jetpack Compose (Material 3), Media3 ExoPlayer and MediaSession, Room, DataStore, OkHttp, kotlinx.serialization, Coil, WorkManager, NewPipe Extractor.

## Changelog

What's new in each release is in [CHANGELOG.md](CHANGELOG.md) and on the [Releases page](https://github.com/Yigder/Prism/releases).

## Contributing

Bug reports, ideas and pull requests are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md) for how to build, test and send changes.

## License

Copyright (C) 2026 Yigder

Prism is free software: you can redistribute it and/or modify it under the terms of the [GNU General Public License](LICENSE) as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version. It is distributed in the hope that it will be useful, but without any warranty; see the license for details.

The license covers Prism's own code. The libraries it's built with keep their own licenses (listed in the app under Settings → About → Open-source licenses). Music, artwork, lyrics and the YouTube and YouTube Music names belong to their respective owners, and Prism is not affiliated with Google or YouTube.
