# Changelog

What changed in each release. Older releases are described on the [Releases page](https://github.com/Yigder/Prism/releases).

## 1.6.1

### New
- **Podcasts.** The round podcasts button at the top of Home, beside Customize, opens them.
  - **Continue listening:** episodes you've started pick up where you left off.
  - **Your shows** and **Episodes for later** from your YouTube Music account, when signed in.
  - **Popular shows and episodes**, by category.
  - **Show pages:** newest episodes first, more as you go, save to library and share.
  - **Search:** new Podcasts and Episodes filters.
  - **Speed:** 0.75× to 2× from the player's ⋯ menu, for episodes only.
  - Episodes don't look up lyrics, don't blend with Mix, aren't followed by music radio and don't count in Replay.
- **Home screen widgets**, in four looks: Prism (frosted cover), Spotify style, Apple Music style and YouTube Music style. Each shows the cover, title, progress, previous/play/next and like; tapping the cover opens the player.
- **Content country** (Settings → Home screen → Region). Where YouTube Music isn't offered, pick a country where it is to get Home, charts and search back. Songs a label blocks in your country still won't play.
- **Mix on mobile data.** "Measure tempo on mobile data" lets beat matching work away from Wi-Fi (about 1–4 MB per song).

### Fixed
- **Pixel Watch:** previous and next sit beside play again, instead of like and shuffle.
- **Lyric timing:**
  - lyric files that shift their own timing (an `[offset]` line) were read without the shift, so every line was early or late;
  - some sources used an extended or radio edit's lyrics, which ran early or late; versions more than 8 seconds off are now turned down, and lyrics saved from those sources are checked again once;
  - while paused, lyrics no longer creep ahead and jump back.
- **Menus:** swiping a menu down (like the player's ⋯) could stutter, or pop back up after being flung away.
- **Player:** an interrupted swipe down could leave the player stuck part of the way down.

## 1.6.0

### New
- **Mix.** Songs blend into each other instead of stopping and starting (Settings → Playback & sound → Mix, off by default).
  - **Blend length:** 2 to 12 seconds.
  - **Match tempo and beats:** Prism measures each song's tempo on the phone, on Wi-Fi. The next song comes in on the beat and plays at the outgoing song's tempo while they overlap (pitch kept), then eases back to its own.
  - **No blend for:** an album's own tracks, live streams, music videos and Listen Together. These still play as before.
- **Translated lyrics.** A translation can show under each lyric line when a song is in another language.
  - **Where:** Settings → Lyrics, or the player's ⋯ menu.
  - **Language:** your phone's language by default, or one of 20 others.
  - **Privacy:** this is a machine translation, and the lyrics' text is sent to Google Translate.
- **"Next in queue".** Songs you add with Play next or Add to queue get their own heading in the queue. Add to queue now puts songs before autoplay's suggestions instead of after them.

### Faster
- **Songs start sooner.** Prism gets the likeliest next song ready ahead of time and, on Wi-Fi, saves its first few seconds. This covers:
  - the top search result;
  - the first song of an open album or playlist;
  - the next song in the queue, including after the queue changes or shuffle is turned on;
  - the song in the mini player when the app opens, and the one after it.
- **Results:** a song that was got ready starts in about half a second instead of over two seconds.

### Fixed
- **Back gesture animation:** the phone's back button or gesture now fades between pages like the back arrow does, instead of shrinking the page like a card.
- **Plain error messages:**
  - "You're offline", instead of technical text;
  - searching with no connection says so, instead of "Search failed";
  - a clear message when YouTube is limiting playback on the network.
- **Refused requests after signing in:** YouTube refused some requests ("Request contains an invalid argument") for some accounts and regions, such as brand accounts and es-419. Prism now retries them in a plainer form.
- **Blocked networks:** when YouTube blocks anonymous playback on a network, signed-in playback goes straight to the account's own clients for a while, instead of failing on each song first.
- **Bitrate:** the player shows each song's real average bitrate. It used to show YouTube's nominal figure (Opus always said 160 kbps).
- **Album tracks:** signed out, some albums' tracks opened as music videos. They now play as songs.
- **Switching between song and video** no longer counts as a new play in history and Replay.
- **Lyrics and queue scrolling:** the player controls no longer flicker in and out while you scroll lyrics or the queue (seen on 120 Hz phones).
- **Home:** a stale "Quick picks" row no longer flashes up while Home loads.
- **"See all" pages:** mixes and playlists now show as a two-column grid of covers, not one sideways row.
- **Fonts:** Google Sans and Rounded are greyed out, with a note, on phones that don't have Google Sans Flex. Picking them there used to do nothing.
- **Counts:** they read "1 song" and "1 artist", not "1 songs".
