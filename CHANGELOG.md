# Changelog

What changed in each release. Older releases are described on the [Releases page](https://github.com/Yigder/Prism/releases).

## 1.6.5

### New
- **Immersive mode** (the player's ⋯ menu, or Settings → Player): the cover fills the whole screen, its animated version first when it has one, and the lyrics play over its lower part a line at a time, like a lyric video. Everything else hides; tap the screen for a settings button at the bottom (back to the normal player, lyrics timing and source, the song's options), or **hold it** until its ring fills to go straight back to the normal player. Swipe left or right for the next or previous song, and drag down to close the player. The cover can fill the screen, be **zoomed out** (the default) or show **whole**, over a blurred copy of itself (the settings button, or Settings → Player). The lyrics can sit **high**, in the **middle** or **low** (the default) on the screen, or run **over the whole screen**: every line, the one being sung large and bright, the list gliding along by itself.
- **Crossfade** (Settings → Playback & sound, formerly Mix) now works like Spotify's: the ending song fades out while the next one fades in, both at their own speed. **Automatic length** (on by default) works out each crossfade from how the songs end and begin: over a song's own fade-out, over the next song's quiet intro after an abrupt ending, and only briefly between two hard edges. Or set 1–12 seconds yourself. Matching tempo and beats is now an option (DJ-style), off unless you turn it on. Songs already measured are measured once more for their fade-outs and intros.
- **Local files** has its own page (Library → Playlists → Local files), with **Songs**, **Artists** and **Albums**. Artists open to their songs album by album, and albums play in track order.
- **Swipe a song** in an album or playlist (Liked songs, Downloads and Local files too): right to **play it next**, left to **add it to the end of the queue**.

### Fixed
- **The player sits higher again,** as before 1.6.4: the cover and the play, back and forward buttons had moved down. The lyrics, Song/Video and queue buttons along the bottom stay where they are, and the title still sits close to the progress bar.
- **Quick picks** showed your recent plays again: YouTube's "Listen again" / "Speed dial" shelf was taken for them. When YouTube has no picks, songs like your latest play stand in, never the songs you just played.
- **Animated covers:** after saving one song's animated cover, the album's other songs showed their still cover. Looking up covers for saved albums and downloads no longer holds up the cover of the song that's playing.
- **Animated covers keep playing** while the ⋯ menu or another sheet is open over the player, instead of switching to the still cover.
- **Shuffle:** Previous went to a song that hadn't played yet. It now goes back to the song played last, including after songs are added to the queue or a song is picked from it, and Play next plays next while shuffled too.

## 1.6.4

### New
- **Cover size** (Settings → Player → Artwork): Standard, **Large** (narrow side margins, like YouTube Music) or **Full width** (the square cover runs edge to edge). With Large or Full width, a hidden volume bar or output device gives its room to the cover (#10).
- **Stream quality** (Settings → Player, where "Stats for nerds" was) shows or hides the codec and bitrate (or "Downloaded") under the progress bar. The stats line over the cover is gone; tap the label, or use the ⋯ menu, for the full stats (#8).
- **Animated covers are saved with downloads**, so downloaded songs keep their moving cover offline. "Save with downloads on mobile data" decides whether that waits for Wi-Fi. An album's songs share one saved copy when they use the album's cover.
- **Quick picks snap** a page of rows at a time when swiped, like YouTube Music (#11).

### Fixed
- **Arabic and other right-to-left lyrics** sit on the right and read right to left, and word-by-word lyrics light up from the right (#7). The line on the player is right-aligned too.
- **Time-synced lyrics from YouTube Music** (LyricFind) are used when a song has them, so songs that only had plain lyrics there now scroll in time, Arabic songs among them (#6).
- **With "Current lyric" off,** the player no longer keeps a blank line for it, and the title sits just above the controls on tall screens (#9).
- **Quick picks** showed your recent plays instead of YouTube's picks when YouTube was in another language (#11).
- **Android Auto's queue** (and the watch's and notification's) follows the shuffled order you hear, and tapping a song in it plays that song.

## 1.6.3

### New
- **Your music files.** Library → **On this phone** lists the music on your phone (MP3, M4A, FLAC, OGG, WAV…) with its own tags and album art, sorted by date added, title, artist or album, with Play and Shuffle. Songs play from the file, with normalization, EQ, Mix, lyrics and Replay. Prism asks once for access and only reads the files.
- **Smart Downloads is one storage space.** It now covers both the songs you stream (the old song cache) and the songs it downloads ahead, under one size (your old two sizes added together). When it's full, the songs you're least likely to play again make room, and liked songs are kept longest. **Smart Downloads on mobile data** lets it download without Wi-Fi, and **Clear Smart Downloads** empties it (songs you downloaded yourself stay).
- **Prism's own icons** everywhere: minimal, rounded line icons in place of Google's.
- **Search:** better top results. Abbreviations work ("adtr" finds A Day to Remember, also "rhcp", "mcr"), YouTube's own top pick is used unless it's a cover or remix you didn't ask for, a song wins over its music video, and suggestions start with your artists and recent searches.
- **Replay** lists your top 100 songs.
- Settings → About → tapping the version opens Prism's GitHub page.

### Fixed
- **Normalize volume** really evens songs out now: each song's loudness is measured on your phone (LUFS, as Spotify and YouTube use) and brought to the same level. YouTube's own figure, used before, hardly differed between loud and quiet songs. The next song's level is ready before it starts. Stats for nerds shows the measured loudness.
- **Mix** blended into silence, or not at all, on songs whose music ends before the file does (a fade-out or a silent tail): blends are now timed from where the music ends, the next song comes straight in if the ending one goes quiet, silence at the start of the next song is skipped, and a slow-loading ending gets more time. "Measure songs on mobile data" (Settings → Playback & sound → Sound) covers Mix and normalization; songs already on your phone are measured without data.
- **Scrubbing** after changing songs could jump too far (it used the first song's length).
- **The player's ⋯ menu** stuck or stuttered when pulled down on songs with animated covers.
- **"Alright" (Kendrick Lamar)** and other long explicit songs froze the player for a moment as they opened, while starred-out words were filled in; songs with long music videos also opened sluggishly.
- **Music videos with a long intro** (like "Alright") have their lyrics lined up again.
- **After a podcast,** music plays as the song again, not its video.
- **Live streams** load again when signed in.
- **Home's frosted background** follows the song that's playing (#3).
- **Search:** the search box has its background back, and a top result's play button turns into pause (#5).
- **Replay:** genre names are readable with a white accent in light mode.

## 1.6.2

Everything since 1.6.0 (1.6.1 was tagged but never released).

### New
- **Podcasts.** The round podcasts button at the top of Home, beside Customize, opens them.
  - **Customize button at the top** (Settings → Home screen) can be turned off; Podcasts then takes its place.
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
- **Karaoke lyrics first.** Lyrics that light up word by word are now called **Karaoke** (Lyrics source shows "Karaoke", "Time-synced lines" or "Plain text"). They win over time-synced lines even when a source earlier in your order only has lines, and songs whose time-synced lyrics were saved before look for karaoke again.
- **Uncensored lyrics:** words starred out whole (`****`, as Apple Music's lyrics do) are filled in now, as in Childish Gambino's "IV. Sweatpants". Songs with a guest in the title find their full words again, and YouTube Music's or LRCLIB's lyrics fill in when Genius doesn't have the song.
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
